# Advanced Order Matching Engine — Product Requirements Document

## Overview

A production-grade, high-performance order matching engine built in Java 21. Targets **500K+ matched orders/second** per instrument on a single node using LMAX Disruptor, Kafka, and Protocol Buffers.

This is the successor to the tutorial engine (`java-order-matching-engine`). That version established correct domain modelling, hexagonal architecture, and safe concurrency. This version replaces the locking model and in-process event bus with a lock-free ring buffer and a Kafka-native pipeline, adds market orders, write-ahead log recovery, and a market-data service.

---

## Goals

- Match limit and market orders at ≥ 500K operations/second per instrument
- Zero database writes on the matching hot path
- Crash recovery via Kafka WAL replay — no state lost on restart
- Clean separation between order intake, matching, and market data
- Observable: throughput metrics, lag monitoring, structured trade events

## Non-Goals (V1)

- Authentication and user account management
- WebSocket real-time streaming (REST snapshots only for V1)
- Cross-instrument or synthetic instrument matching
- Multi-node distributed matching
- JPA, RDBMS, or any relational persistence
- Advanced order types (iceberg, stop, TWAPs)

---

## Architecture

### Service Map

```
                     ┌──────────────────┐
  REST / gRPC ──────▶│  order-service   │──────▶ Kafka: orders
                     └──────────────────┘
                              ▲
                      Redis (open order cache
                       for fast cancel lookup)

                                        Kafka: orders
                                              │
                                              ▼
                                 ┌────────────────────────┐
                                 │     matching-engine     │
                                 │                        │
                                 │  Kafka Consumer        │
                                 │       │                │
                                 │       ▼                │
                                 │  RingBuffer<OrderEvent>│
                                 │       │                │
                                 │       ▼                │
                                 │  OrderHandler          │
                                 │  (single thread)       │
                                 │       │                │
                                 │  OrderBook per         │
                                 │  instrument            │
                                 └────────────────────────┘
                                        │          │
                              ┌─────────┘          └──────────┐
                              ▼                               ▼
                     Kafka: trades                  Kafka: cancels
                              │
                    ┌─────────┴──────────┐
                    ▼                    ▼
          market-data-service      (future: risk-service,
          OHLCV + book snapshots    settlement-service)
```

### Services

| Service | Responsibility |
|---------|----------------|
| `order-service` | REST/gRPC inbound, order validation, publishes to `orders` Kafka topic, Redis cache for open orders |
| `matching-engine` | Consumes `orders` topic, runs Disruptor pipeline, publishes to `trades` + `cancels` topics. No HTTP server. |
| `market-data-service` | Consumes `trades`, builds OHLCV candlesticks, serves order book depth snapshots via REST |
| `proto` | Shared module — `.proto` schema definitions compiled to Java by both services |

---

## Matching Engine Internals

### LMAX Disruptor Pipeline

The Disruptor replaces the `ReentrantLock` from the tutorial version. Key properties:

- **`ProducerType.MULTI`** — multiple Kafka consumer threads may publish concurrently to the ring buffer
- **`BusySpinWaitStrategy`** — lowest possible latency; trades a dedicated CPU core for sub-microsecond wake-up. Use `SleepingWaitStrategy` in development to avoid burning CPU
- **Ring buffer size: 131,072** (2¹⁷) — must be a power of 2; tunable upward for burst tolerance
- **Single `OrderHandler` per ring buffer** — processes all events on one thread, eliminating all locking inside the matching logic
- **`OrderEvent` pre-allocation** — ring buffer slots are pre-allocated at startup; each slot is reused after processing (`event.clear()`), producing zero GC pressure on the hot path

```
Kafka consumer thread(s)
      │
      │  ringBuffer.next() → claim slot
      │  event.setOrder(payload)
      │  ringBuffer.publish(sequence)
      ▼
RingBuffer<OrderEvent>  [131,072 slots, pre-allocated]
      │
      ▼
OrderHandler.onEvent()  [single thread — no locks]
      │
      ├── action == CANCEL  → handleCancel()
      └── action == NEW
              ├── orderType == LIMIT  → matchOrders() → rest remainder
              └── orderType == MARKET → matchMarketOrder() → cancel remainder
```

### Order Book Data Structure

```java
// Per instrument, inside OrderHandler
TreeMap<Integer, ArrayDeque<Order>> buyOrders  // Comparator.reverseOrder() — highest bid first
TreeMap<Integer, ArrayDeque<Order>> sellOrders // natural order — lowest ask first
```

- Price represented as **integer ticks** (not `BigDecimal`) — no heap allocation, no rounding error on the hot path
- O(log N) price-level lookup via `TreeMap.firstEntry()`
- O(1) FIFO order access within a level via `ArrayDeque.peekFirst()` / `pollFirst()`
- **Future optimization**: replace `TreeMap` with a fixed-size array indexed by price tick — O(1) level lookup at the cost of memory

### Matching Algorithm (Price-Time Priority)

1. Incoming **BUY limit**: match against asks where `ask.price ≤ order.price`, oldest first
2. Incoming **SELL limit**: match against bids where `bid.price ≥ order.price`, oldest first
3. Trade executes at the **resting (maker) order's price**
4. Remainder rests on the book (limit) or is cancelled (market)
5. Emit one `Trade` per fill; batch and publish to Kafka

### Batching Strategy

Publishing to Kafka on every single trade would saturate the producer with tiny messages. Batching amortises the cost:

| Event type | Batch size | Also flush on |
|------------|-----------|---------------|
| Trades | 100 | `endOfBatch` |
| Cancellations | 1,000 | `endOfBatch` |

`endOfBatch` is signalled by the Disruptor when the ring buffer is momentarily drained — guarantees bounded latency even under low load.

---

## Wire Format — Protocol Buffers

All inter-service messages use Protobuf binary encoding (~3–5× smaller than JSON, schema-enforced).

### Proto definitions (`proto/src/main/proto/`)

```protobuf
// order.proto
syntax = "proto3";

enum Side        { BUY = 0; SELL = 1; }
enum OrderType   { LIMIT = 0; MARKET = 1; }
enum OrderAction { NEW = 0; CANCEL = 1; }

message OrderRequest {
  int64       orderId    = 1;
  string      instrument = 2;
  Side        side       = 3;
  OrderType   orderType  = 4;
  OrderAction action     = 5;
  int32       price      = 6;  // integer ticks
  int32       quantity   = 7;
  int64       timestamp  = 8;  // epoch nanos
  int64       createdAt  = 9;
}

// trade.proto
message TradeResponse {
  string tradeId     = 1;
  string instrument  = 2;
  int64  buyOrderId  = 3;
  int64  sellOrderId = 4;
  int32  price       = 5;
  int32  quantity    = 6;
  Side   makerSide   = 7;
  int64  executedAt  = 8;
}

message TradeResponseBatch {
  repeated TradeResponse trades = 1;
}

message CancelledOrder {
  int64  orderId    = 1;
  string instrument = 2;
  int32  remaining  = 3;
  int64  cancelledAt = 4;
}
```

### Kafka Topics

| Topic | Producer | Consumer | Format |
|-------|----------|----------|--------|
| `orders` | order-service | matching-engine | `OrderRequest` (Protobuf) |
| `trades` | matching-engine | market-data-service | `TradeResponseBatch` (Protobuf) |
| `cancels` | matching-engine | order-service, market-data-service | `CancelledOrder` (Protobuf) |

**Partition key** = `instrument` on all topics — preserves time-ordering per order book; downstream consumers process each instrument independently.

---

## Persistence — Write-Ahead Log via Kafka

There is no database. Kafka topics are the source of truth.

### Startup Replay

On matching-engine startup:

1. Seek `orders` consumer to **offset 0** on all partitions
2. Replay all historical `OrderRequest` messages through the Disruptor pipeline
3. Rebuild in-memory order books to their last known state
4. Switch to live consumption at the high-water mark
5. Begin publishing to `trades` / `cancels`

### Idempotency on Replay

- Every `OrderRequest` carries a stable `orderId` (int64, client-generated or order-service-assigned)
- `OrderHandler` maintains a `processedOrderIds` set; skips already-applied orders during replay
- Trades already published are not re-emitted (replay only rebuilds book state, does not re-match)

### Retention Policy

| Topic | Retention |
|-------|-----------|
| `orders` | Infinite (or until compacted) — needed for full replay |
| `trades` | Long-term (audit log) |
| `cancels` | 7 days (operational) |

---

## Infrastructure

### docker-compose stack

```yaml
services:
  kafka:       # KRaft mode, no ZooKeeper
  redis:       # order-service open-order cache
  order-service:
  matching-engine:
  market-data-service:
```

### Redis (order-service only)

- Stores `orderId → OrderStatus` for open orders
- Enables O(1) cancel validation without querying Kafka
- TTL = order lifetime; evicted when order is filled or cancelled
- Matching engine does not touch Redis — it is not on the hot path

---

## JVM Tuning

```bash
java \
  -Xms30G -Xmx30G \
  -XX:+UseZGC -XX:+ZGenerational \
  -XX:ZUncommitDelay=30 \
  -XX:ConcGCThreads=4 \
  -Dfile.encoding=UTF-8 \
  -jar matching-engine.jar
```

| Flag | Effect |
|------|--------|
| `-Xms30G -Xmx30G` | Pre-allocate heap — eliminates heap resize pauses during warmup |
| `-XX:+UseZGC` | Region-based, concurrent GC; sub-millisecond pauses at large heap |
| `-XX:+ZGenerational` | ZGC generational mode — faster collection for short-lived allocations |
| `-XX:ZUncommitDelay=30` | Retain committed pages 30s after shrink — avoids re-commit cost on burst |
| `-XX:ConcGCThreads=4` | 4 background GC threads — tunable based on core count |

**Additional OS-level tuning for production:**
- Pin matching thread to an isolated CPU core (`taskset` / `numactl`)
- Disable transparent huge pages
- Set NIC interrupt affinity away from matching core

---

## Implementation Phases

| Phase | Module | Deliverables |
|-------|--------|-------------|
| 1 | `proto` | `.proto` definitions for `OrderRequest`, `TradeResponse`, `TradeResponseBatch`, `CancelledOrder`; Gradle protobuf plugin generating Java sources |
| 2 | `matching-engine` | Disruptor config (`DisruptorConfig`), `OrderEvent`, `OrderHandler` with full limit + market order matching and cancel; `OrderBook` with `TreeMap<Integer, ArrayDeque>`; unit tests for all matching scenarios |
| 3 | `matching-engine` | Kafka consumer (`OrderConsumer`) reading `orders` topic; `TradePublisher` batching to `trades` + `cancels` topics; integration test with `@EmbeddedKafka` |
| 4 | `order-service` | REST API (`POST /orders`, `DELETE /orders/{id}`); order validation; publishes `OrderRequest` protobuf to `orders` topic; Redis open-order cache |
| 5 | `matching-engine` | WAL replay on startup — seek to offset 0, rebuild order books, idempotency guard; integration test: shutdown → restart → assert book state preserved |
| 6 | `market-data-service` | Kafka consumer on `trades`; OHLCV aggregation; `GET /orderbook/{instrument}` depth snapshot; `GET /trades/{instrument}` recent fills |
| 7 | All | JVM tuning flags; driver service for load generation; throughput benchmark targeting 500K ops/sec; latency p99 measurement |
| 8 | `order-service` | Redis TTL-based open-order eviction; cancel acknowledgement via `cancels` topic consumer; order status query endpoint |

---

## Key Design Decisions

| Concern | Decision | Rationale |
|---------|----------|-----------|
| Concurrency model | Single-threaded `OrderHandler` per Disruptor | Eliminates all locking inside matching; cache-friendly sequential access |
| Wait strategy | `BusySpinWaitStrategy` (prod), `SleepingWaitStrategy` (dev) | Lowest latency in prod; CPU-friendly in dev |
| Price representation | `int32` ticks | No heap allocation, no `BigDecimal` GC pressure, O(1) integer comparison |
| Serialization | Protobuf binary | 3–5× smaller than JSON; schema-enforced across services |
| GC | ZGC generational, 30GB pre-allocated heap | Sub-millisecond pauses; critical for consistent matching latency |
| Batching | 100 trades / 1,000 cancels | Reduces Kafka producer round-trips without unbounding latency |
| Persistence | Kafka WAL + startup replay | No DB on hot path; Kafka provides durability, ordering, and replayability |
| Order book | `TreeMap<Integer, ArrayDeque<Order>>` | Correct and simple; O(log N) level lookup, O(1) FIFO within level |
| Future O(1) book | Array-indexed price levels | Swap in when profiling shows `TreeMap` is the bottleneck |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Ring buffer overflow on burst | Monitor fill ratio (`ringBuffer.remainingCapacity()`); apply backpressure at Kafka consumer (pause partition consumption when buffer > 80% full) |
| Duplicate order on WAL replay | `orderId` idempotency set in `OrderHandler`; skip orders already applied |
| Price tick overflow (`int32`) | Define `tickSize` and `maxPrice` per instrument in config; validate at order-service boundary before publishing |
| `BusySpinWaitStrategy` burns CPU on dev machine | Default to `SleepingWaitStrategy` via config profile; switch to `BusySpinWaitStrategy` only on production profile |
| Kafka consumer lag building up | Per-instrument partitioning; monitor `consumer_lag` metric; alert when lag > N |
| WAL replay time on large topic | Snapshot order book state periodically to a compacted topic; replay from latest snapshot offset |
| Proto schema evolution | Use `reserved` field numbers for removed fields; additive changes only; version topics if breaking changes needed |

---

## Comparison: Tutorial vs Advanced

| Concern | Tutorial (`java-order-matching-engine`) | Advanced (this) |
|---------|----------------------------------------|-----------------|
| Concurrency | `ReentrantLock` per instrument | LMAX Disruptor — lock-free ring buffer |
| Price type | `BigDecimal` | `int32` ticks |
| Order intake | REST → in-process | Kafka → Disruptor |
| Event publishing | Spring `ApplicationEventPublisher` | Kafka `trades` topic (Protobuf batches) |
| Persistence | In-memory only | Kafka WAL + startup replay |
| Architecture | Single service | Microservices (order, engine, market-data) |
| Throughput target | Correctness-first | 500K ops/sec |
| GC | Default JVM GC | ZGC generational, 30GB heap |
| Serialization | JSON | Protocol Buffers |

The tutorial version's hexagonal architecture, domain invariants, matching algorithm, and event model carry forward unchanged. The advanced version is an infrastructure and performance upgrade — not a rewrite of the domain logic.
