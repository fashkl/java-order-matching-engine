# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./gradlew build

# Run application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "matchingengine.matchingengine.MatchingEngineApplicationTests"

# Run a specific test method
./gradlew test --tests "matchingengine.matchingengine.MatchingEngineApplicationTests.contextLoads"

# Clean build
./gradlew clean build

# Package as executable JAR
./gradlew bootJar
```

## Project Overview

This is a **Java 21 Spring Boot 4.x** matching engine — currently in early scaffold stage. The build uses **Gradle 9.4.1 with Kotlin DSL** (`build.gradle.kts`). Tests use **JUnit 5** via `@SpringBootTest`.

Main source root: `src/main/java/matchingengine/matchingengine/`  
Test root: `src/test/java/matchingengine/matchingengine/`

## Architecture Intent

This project is meant to implement a financial order matching engine. When building out the system, the expected layers are:

- **Domain layer** — `Order`, `OrderBook`, `Trade` entities with strict invariants (price > 0, quantity > 0, no negative balances)
- **Matching engine core** — price-time priority algorithm using sorted structures (`TreeMap<Price, Queue<Order>>` for bids/asks)
- **Service layer** — stateful `OrderBookService` per instrument; must be thread-safe (concurrent order submissions)
- **API layer** — REST or WebSocket endpoints for order submission and market data

## Financial System Invariants

When implementing matching logic, enforce these invariants at all times:

1. A fill never exceeds the available quantity on either side.
2. Order state transitions are: `NEW → PARTIALLY_FILLED → FILLED` or `NEW → CANCELLED`. No other transitions.
3. Balance updates (if added) must be atomic and transactional — debit and credit in the same transaction.
4. Matching must be idempotent per order ID to prevent double-fills on retries.

## Concurrency Notes

The order book is a shared mutable data structure. Any future implementation must:
- Protect per-instrument `OrderBook` with a reentrant lock or use a single-threaded actor model per book.
- Avoid holding locks during I/O (DB writes, event publishing).
- Consider `ConcurrentSkipListMap` as an alternative to `TreeMap` + explicit locking.
