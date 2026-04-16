# Order Matching Engine

A financial limit order matching engine built with Java 21 and Spring Boot 4. Implements **price-time priority (FIFO)** matching across multiple instruments.

---

## Table of Contents

- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Implementation Phases](#implementation-phases)
- [Extending the Engine](#extending-the-engine)

---

## Architecture

The project follows **Hexagonal Architecture (Ports and Adapters)**:

```
┌─────────────────────────────────────────────────────────────┐
│                        Adapters (in)                        │
│         REST Controllers · Event Listeners                  │
├─────────────────────────────────────────────────────────────┤
│                    Application Layer                        │
│         Use Case Interfaces (Ports) + Service               │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                           │
│     Order · Trade · OrderBook · MatchingEngine              │
│     DomainEvent · DomainEventPublisher                      │
├─────────────────────────────────────────────────────────────┤
│                    Adapters (out)                           │
│     In-Memory Repositories · Spring Event Bus              │
└─────────────────────────────────────────────────────────────┘
```

**Key design decisions:**
- The domain layer has **zero framework dependencies** — pure Java
- The application service depends only on port interfaces, never on concrete adapters
- Swapping persistence (in-memory → JPA or Kafka) only requires a new outbound adapter — no changes to domain or application code
- `MatchingEngine` is **stateless** — takes an `Order` and `OrderBook`, returns a list of `Trade` objects
- `DomainEvent` is a `sealed interface` — the compiler enforces that all event types are handled

### Matching Algorithm

Price-time priority (FIFO):
1. Incoming **BUY** matches against the lowest-priced asks first
2. Incoming **SELL** matches against the highest-priced bids first
3. Within the same price, the **oldest resting order fills first**
4. Trade executes at the **resting order's price** (passive side sets the price)
5. Any unfilled remainder rests on the book

### Event Flow

```
submit(order)
    │
    ├── [lock] match() → List<Trade>
    └── [unlock]
        │
        └── publish TradeExecutedEvent (per fill)
                │
                ├── TradeAuditListener   → logs fill (async)
                └── (add more listeners without touching the engine)
```

---

## Getting Started

### Prerequisites

- Java 21+
- Gradle (wrapper included)

### Build

```bash
./gradlew build
```

### Run

```bash
./gradlew bootRun
```

The server starts on `http://localhost:8080`.

### Swagger UI

Once running, open:

```
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI spec:

```
http://localhost:8080/v3/api-docs
```

### Run Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "matchingengine.matchingengine.domain.MatchingEngineTest"

# Specific test method
./gradlew test --tests "matchingengine.matchingengine.domain.MatchingEngineTest.multipleFills_incomingSwepsMultiplePriceLevels"
```

---

## API Reference

### Submit an Order

```http
POST /api/orders
Content-Type: application/json

{
  "instrument": "BTC-USD",
  "side": "BUY",
  "price": "45000.00",
  "quantity": "1.5"
}
```

**Response `200 OK`:**
```json
{
  "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "PARTIALLY_FILLED",
  "remainingQty": "0.5",
  "trades": [
    {
      "tradeId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "price": "44990.00",
      "quantity": "1.0"
    }
  ]
}
```

| Status | Meaning |
|--------|---------|
| `NEW` | No match — order rests on the book |
| `PARTIALLY_FILLED` | Some qty matched, remainder rests on the book |
| `FILLED` | Fully matched — off the book |

**Error responses:** `400` for invalid input (negative price, zero quantity, unknown side)

---

### Cancel an Order

```http
DELETE /api/orders/{id}
```

**Response `204 No Content`** — order cancelled.
**Response `404 Not Found`** — unknown order ID.

Idempotent — cancelling a filled or already-cancelled order returns `204` with no state change.

---

### Get Order Book

```http
GET /api/orderbook/{instrument}
```

**Response `200 OK`:**
```json
{
  "instrument": "BTC-USD",
  "bids": [
    { "price": "45000.00", "totalQuantity": "3.0", "orderCount": 2 },
    { "price": "44900.00", "totalQuantity": "1.5", "orderCount": 1 }
  ],
  "asks": [
    { "price": "45100.00", "totalQuantity": "2.0", "orderCount": 1 }
  ]
}
```

Bids are sorted highest price first. Asks are sorted lowest price first.

**Response `404 Not Found`** — no orders have been submitted for this instrument.

---

## Project Structure

```
src/main/java/matchingengine/matchingengine/
│
├── domain/                          # Pure domain — no framework deps
│   ├── Order.java                   # Entity: lifecycle (fill, cancel), invariant guards
│   ├── Trade.java                   # Immutable fill record
│   ├── OrderBook.java               # TreeMap<Price, Deque<Order>> per side
│   ├── MatchingEngine.java          # Stateless matching algorithm
│   ├── Side.java                    # BUY | SELL
│   ├── OrderStatus.java             # NEW | PARTIALLY_FILLED | FILLED | CANCELLED
│   ├── events/
│   │   ├── DomainEvent.java         # sealed interface — compiler-enforced event types
│   │   └── TradeExecutedEvent.java  # record: eventId, occurredAt, trade
│   └── ports/
│       └── DomainEventPublisher.java  # outbound port — domain owns the abstraction
│
├── application/
│   ├── port/in/                     # Inbound ports (use case interfaces)
│   │   ├── SubmitOrderUseCase.java
│   │   ├── CancelOrderUseCase.java
│   │   └── GetOrderBookUseCase.java
│   ├── port/out/                    # Outbound ports (repository interfaces)
│   │   ├── OrderRepositoryPort.java
│   │   └── OrderBookRepositoryPort.java
│   └── service/
│       └── OrderApplicationService.java   # implements use cases, per-instrument locking
│
├── adapter/
│   ├── in/
│   │   ├── rest/                    # Inbound: HTTP
│   │   │   ├── OrderController.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── dto/
│   │   └── event/                   # Inbound: domain events
│   │       └── TradeAuditListener.java  # @Async @TransactionalEventListener
│   └── out/
│       ├── persistence/             # Outbound: storage (in-memory)
│       │   ├── InMemoryOrderRepository.java
│       │   └── InMemoryOrderBookRepository.java
│       └── eventbus/                # Outbound: event publishing
│           └── SpringDomainEventPublisher.java
│
├── config/
│   └── AppConfig.java               # @Configuration @EnableAsync — wires all beans
│
└── exception/
    └── OrderNotFoundException.java
```

---

## Implementation Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Core domain: `Order`, `Trade`, `OrderBook`, `MatchingEngine` | ✅ Done |
| 2 | Order management service + hexagonal architecture (ports & adapters) | ✅ Done |
| 3 | REST API (`POST /orders`, `DELETE /orders/{id}`, `GET /orderbook/{instrument}`) + Swagger | ✅ Done |
| 4 | Concurrency — per-instrument `ReentrantLock`, `ConcurrentHashMap`, defensive open-check | ✅ Done |
| 5 | Domain events — `sealed DomainEvent`, `TradeExecutedEvent` with metadata (`eventId`, `occurredAt`), `@Async @TransactionalEventListener` audit listener | ✅ Done |
| 6 | Kafka event log — publish `TradeExecutedEvent` to Kafka topic as durable append-only log | 🔄 In Progress |
| 7 | Persistence (JPA/WAL) | ⏭ Deferred to advanced version |

---

## Extending the Engine

### Why persistence is deferred

The matching hot path is intentionally free of I/O. Adding synchronous DB writes inside the matching lock would introduce latency for every order submission — the opposite of what a matching engine needs.

**The port/adapter pattern makes swapping trivial.** To add JPA persistence for orders, implement `OrderRepositoryPort` with a JPA adapter — the `OrderApplicationService` and domain don't change at all:

```java
// adapter/out/persistence/JpaOrderRepository.java
public class JpaOrderRepository implements OrderRepositoryPort {
    // Spring Data JPA implementation
}
// Then swap the bean in AppConfig — done.
```

**For trades**, the right pattern is already in place: `TradeAuditListener` runs `@Async` after every fill. Add a `TradePersistenceListener` alongside it to write trades to a DB or publish to Kafka — zero changes to the engine.

### Advanced version (coming next)

A separate repository will implement the advanced version using:
- **LMAX Disruptor** — lock-free ring buffer, single-threaded matching per instrument
- **Array-based price levels** — O(1) insert/cancel instead of `TreeMap`
- **Kafka** — trades published as events, consumed by downstream systems
- **Write-ahead log** — append-only persistence, not JPA

See [`docs/ORDER_MATCHING_ENGINE_PRD.md`](docs/ORDER_MATCHING_ENGINE_PRD.md) for the full design document.
