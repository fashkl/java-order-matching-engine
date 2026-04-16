# Order Matching Engine — Product Requirements Document

## Overview

A financial order matching engine built in Java 21 with Spring Boot. Implements price-time priority (FIFO) matching for limit orders across multiple instruments. Designed to scale incrementally from a pure in-memory core to a persistent, concurrent, event-driven system.

---

## Goals

- Correctly match buy and sell limit orders using price-time priority.
- Support full fills, partial fills, and cancellations.
- Expose a clean REST API for order submission and market data.
- Be safe under concurrent order submissions per instrument.
- Decouple trade notification from matching logic.

## Non-Goals (for now)

- Market orders, stop orders, or advanced order types.
- Cross-instrument matching or synthetic instruments.
- Real-time WebSocket market data streaming.
- Multi-node/distributed matching.
- Authentication or user account management.

---

## Phases

### Phase 1 — Core Domain (Pure Java, no Spring)

**Goal:** Model the domain and implement price-time priority matching with no framework dependencies.

**Deliverables:**

| Component | Description |
|-----------|-------------|
| `Order` | id, instrument, side (BUY/SELL), price, quantity, remainingQty, status, timestamp |
| `Trade` | buyOrderId, sellOrderId, instrument, executionPrice, quantity |
| `OrderBook` | Per-instrument; bids as `TreeMap<BigDecimal, Deque<Order>>` (descending), asks (ascending) |
| `MatchingEngine` | Pure matching logic — walks opposite side, emits `Trade` objects, updates quantities |

**Matching Algorithm (price-time priority):**
1. Incoming BUY: match against asks with price ≤ order price, oldest first.
2. Incoming SELL: match against bids with price ≥ order price, oldest first.
3. Fill as much quantity as possible. Remainder stays on the book.
4. Emit one `Trade` per matched level/order.

**Invariants:**
- A fill never exceeds `remainingQty` on either side.
- Order status transitions: `NEW → PARTIALLY_FILLED → FILLED` or `NEW → CANCELLED`. No other transitions.
- `price` and `quantity` must be positive. `remainingQty` must be ≥ 0.

**Test Scenarios:**
- Full fill (exact quantity match).
- Partial fill (incoming order larger than resting order).
- No match (price does not cross).
- Multiple fills from a single incoming order sweeping multiple price levels.
- Resting order partially filled by multiple incoming orders.

---

### Phase 2 — Order Management Service

**Goal:** Stateful lifecycle management layered on top of Phase 1.

**Deliverables:**

| Component | Description |
|-----------|-------------|
| `OrderRegistry` | In-memory `Map<OrderId, Order>` for lookup and cancel |
| `OrderBookService` | One `OrderBook` + `MatchingEngine` per instrument; routes incoming orders and cancellations |
| `TradeLog` | In-memory list of `Trade` objects with append and query |

**Cancel Behaviour:**
- Cancel removes the order from the book and sets status to `CANCELLED`.
- Cancelling a `FILLED` or already `CANCELLED` order is a no-op (idempotent).
- Cancelling an unknown order ID returns a clear error — not a silent failure.

**Test Scenarios:**
- Cancel before any match.
- Cancel after partial fill (only remainder is cancelled).
- Cancel a filled order — no state change.
- Cancel an unknown order ID.

---

### Phase 3 — REST API (Spring)

**Goal:** Expose the engine via HTTP.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/orders` | Submit a new limit order. Returns order ID and any immediate fills. |
| `DELETE` | `/orders/{id}` | Cancel an open order by ID. |
| `GET` | `/orderbook/{instrument}` | Current bids/asks (top N price levels). |
| `GET` | `/trades` | Recent trade history (latest N trades). |

**Request — POST /orders:**
```json
{
  "instrument": "BTC-USD",
  "side": "BUY",
  "price": "45000.00",
  "quantity": "1.5"
}
```

**Response — POST /orders:**
```json
{
  "orderId": "uuid",
  "status": "PARTIALLY_FILLED",
  "remainingQty": "0.5",
  "trades": [
    { "price": "44990.00", "quantity": "1.0", "tradeId": "uuid" }
  ]
}
```

**Error Handling:**
- `400` for invalid input (missing fields, negative price/quantity).
- `404` for cancel on unknown order ID.
- `409` for cancel on an already filled or cancelled order.

**Tests:**
- `@WebMvcTest` controller slice tests for each endpoint.
- One end-to-end flow: submit two crossing orders, assert trade is returned.

---

### Phase 4 — Concurrency & Thread Safety

**Goal:** Make the engine safe under concurrent order submission per instrument.

**Design:**
- Each `OrderBookService` instance holds a `ReentrantLock` per instrument.
- The lock is acquired for the full match-and-update cycle.
- No I/O (DB writes, event publishing) is performed while holding the lock.
- Separate instruments never contend on the same lock.

**Deliverables:**
- Per-instrument locking in `OrderBookService`.
- Lock scope documented with comments at acquisition and release points.

**Test Scenarios:**
- Concurrent submits on the same instrument — assert no double-fills, no lost orders, no negative `remainingQty`.
- Concurrent submits on different instruments — assert no cross-instrument interference.
- Concurrent submit and cancel — assert cancel wins or submit wins cleanly, never partial corruption.

---

### Phase 5 — Events & Observability

**Goal:** Decouple trade notifications from the matching core.

**Design:**
- `MatchingEngine` returns a list of `Trade` objects — it does not publish events itself.
- `OrderBookService` publishes a `TradeEvent` via Spring `ApplicationEventPublisher` after the lock is released.
- Consumers (logging, audit, future WebSocket push) register as `@EventListener` — no coupling to the engine.

**Deliverables:**

| Component | Description |
|-----------|-------------|
| `TradeEvent` | Wraps a `Trade`; published on every fill |
| `TradeAuditListener` | Logs trade details for observability |

**Test Scenarios:**
- Assert event is published exactly once per fill.
- Assert no event is published when there is no match.
- Assert event is published after lock release (not inside the lock).

---

### Phase 6 — Persistence *(intentionally deferred)*

**Decision:** Persistence is deferred to the advanced Disruptor version. Adding synchronous DB writes to the matching hot path contradicts the engine's core design principle — the lock must protect only state mutation, not I/O.

**The correct patterns for each concern:**

| Concern | Right approach |
|---------|---------------|
| Trade durability | `TradePersistenceListener` reacting to `TradeExecutedEvent` `@Async` — fire-and-forget, no lock involvement |
| Trade history queries | Kafka topic consumed by a read model (CQRS) |
| Order state durability | Write-ahead log in the advanced engine, not JPA |
| Port swap demo | Implement `OrderRepositoryPort` with JPA — `OrderApplicationService` unchanged |

**Why not JPA on the hot path:**
- Every `submit()` call would block inside the lock waiting for a DB write
- JPA introduces GC pressure (object mapping) on the latency-critical path
- Relational DBs are not the right tool for append-only trade event streams

**Advanced version** will address persistence correctly with LMAX Disruptor + Kafka + WAL.

**Invariants:**
- Trade write and order status update must be atomic (same transaction).
- Replay on startup must produce the same order book state as before shutdown.
- No duplicate trade writes on retry — use trade ID as idempotency key.

**Deferred until:** Phases 1–5 are stable and tested.

---

## Key Data Types

```
Order
  id:           UUID
  instrument:   String          (e.g. "BTC-USD")
  side:         BUY | SELL
  price:        BigDecimal      (never null for limit orders)
  quantity:     BigDecimal      (original, immutable after creation)
  remainingQty: BigDecimal      (mutable, starts == quantity)
  status:       NEW | PARTIALLY_FILLED | FILLED | CANCELLED
  createdAt:    Instant

Trade
  id:           UUID
  instrument:   String
  buyOrderId:   UUID
  sellOrderId:  UUID
  price:        BigDecimal      (price of the resting order)
  quantity:     BigDecimal
  executedAt:   Instant
```

---

## Risks & Open Questions

| Risk | Mitigation |
|------|------------|
| Incorrect partial fill logic leaving `remainingQty < 0` | Enforce invariant check after every fill; fail-fast in tests |
| Lock contention on high-volume instruments | Per-instrument lock limits contention; revisit with actor model if needed |
| Order book state diverges from DB on crash (Phase 6) | Rebuild from DB on startup; consider write-ahead log later |
| BigDecimal rounding inconsistency | Define a single `RoundingMode` and scale constant used everywhere |
| Missing idempotency on order submit | Generate order ID client-side or use idempotency key header |

---

## Implementation Order

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
  Core       Mgmt      API      Concurrency  Events  Persistence
```

Each phase must be fully tested before starting the next.
