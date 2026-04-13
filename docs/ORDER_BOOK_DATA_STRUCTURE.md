# Order Book Data Structure — Explained

This document explains how `OrderBook.java` works internally, with concrete examples.
Intended for readers who are not deeply familiar with Java collections.

---

## The Problem

At any moment, the engine needs to answer instantly:
1. **What is the highest price a buyer is willing to pay?** (best bid)
2. **What is the lowest price a seller will accept?** (best ask)
3. **Among orders at the same price, who arrived first?** (time priority)

The data structure is designed to answer all three in O(1) or O(log n).

---

## Layer 1 — `TreeMap` (sorted by price)

A `TreeMap` is a sorted dictionary. Every time you insert or remove a key, it stays sorted automatically. No manual sorting needed.

### Asks side — natural (ascending) order

```
TreeMap for asks (sellers):

  Price   → Orders waiting at that price
  ──────────────────────────────────────
  100.00  → [Order-A, Order-B]
  101.00  → [Order-C]
  105.00  → [Order-D]

  .firstEntry() = 100.00  ← cheapest seller = best ask
```

### Bids side — reverse (descending) order

```
TreeMap for bids (buyers):

  Price   → Orders waiting at that price
  ──────────────────────────────────────
  102.00  → [Order-E]
  100.00  → [Order-F, Order-G]
  98.00   → [Order-H]

  .firstEntry() = 102.00  ← highest buyer = best bid
```

### Why not a List or HashMap?

| Structure | Find best price | Insert | Remove |
|-----------|----------------|--------|--------|
| `List`    | O(n) scan      | O(1)   | O(n)   |
| `HashMap` | O(n) scan      | O(1)   | O(1)   |
| `TreeMap` | **O(1)**       | O(log n) | O(log n) |

`TreeMap` wins because it keeps prices sorted automatically. `.firstEntry()` always returns the best price without scanning.

---

## Layer 2 — `Deque<Order>` (sorted by time within a price)

At each price level, many orders may be queued. We serve the **oldest first** — that is time priority.

A `Deque` (pronounced "deck") is a double-ended queue. Think of it as a **line at a counter**:
- New orders join at the **back** → `addLast(order)`
- Matching takes from the **front** → `pollFirst()`

```
Price level 100.00 — orders arriving over time:

  09:00 → Order-A arrives  →  [A]
  09:05 → Order-B arrives  →  [A, B]
  09:10 → Order-C arrives  →  [A, B, C]

  Match at 09:11:
    Order-A consumed (oldest) →  [B, C]
  Match at 09:12:
    Order-B consumed          →  [C]
```

### Why not a List?

`List.remove(0)` shifts every remaining element — O(n).
`ArrayDeque.pollFirst()` removes from the front in O(1) with no shifting.

---

## Full Example — 3 buys then a sell

```
Step 1: BUY 5 @ $100.00 arrives
  bids: { 100.00 → [Order-1] }

Step 2: BUY 3 @ $102.00 arrives
  bids: { 102.00 → [Order-2],
          100.00 → [Order-1] }

Step 3: BUY 2 @ $100.00 arrives (same price as Order-1)
  bids: { 102.00 → [Order-2],
          100.00 → [Order-1, Order-3] }
                              ↑
                         Order-3 queues behind Order-1

Step 4: SELL 4 @ $101.00 arrives
  Best bid = $102.00 ≥ $101.00 → prices cross → match!
    Order-2 fills: Trade(price=$102, qty=3)
    remainingQty of SELL = 4 - 3 = 1

  Next best bid = $100.00 < $101.00 → prices don't cross → stop

  SELL remainder (qty=1) rests on the ask side.

Final state:
  bids: { 100.00 → [Order-1, Order-3] }
  asks: { 101.00 → [SELL-remainder-qty-1] }
```

---

## Price Level Cleanup

When the last order at a price is removed, the price key itself is deleted from the map.

```
Before:  bids = { 102.00 → [Order-2] }
Remove Order-2:
After:   bids = {}   ← key 102.00 is gone, not left as an empty entry
```

This ensures `getBestBid()` never returns a stale empty level.

---

## Summary

```
OrderBook
├── bids: TreeMap (HIGH → LOW)
│   ├── 102.00 → Deque [Order-2]              ← best bid (.firstEntry())
│   └── 100.00 → Deque [Order-1, Order-3]
└── asks: TreeMap (LOW → HIGH)
    ├── 101.00 → Deque [Order-4]              ← best ask (.firstEntry())
    └── 105.00 → Deque [Order-5, Order-6]
```

The `TreeMap` answers *"at what price?"* and the `Deque` answers *"in what order?"*.
