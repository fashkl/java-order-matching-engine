package matchingengine.matchingengine.domain;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class OrderBook {

    private final String instrument;

    // Best bid = highest price → reverse order
    private final TreeMap<BigDecimal, Deque<Order>> bids =
            new TreeMap<>(Collections.reverseOrder());

    // Best ask = lowest price → natural order
    private final TreeMap<BigDecimal, Deque<Order>> asks =
            new TreeMap<>();

    public OrderBook(String instrument) {
        this.instrument = instrument;
    }

    /**
     * Add an open order to the book.
     * The order is appended to the tail of its price level queue (time priority).
     */
    public void addOrder(Order order) {
        sideFor(order.getSide())
                .computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>())
                .addLast(order);
    }

    /**
     * Remove an order from the book (cancel or post-fill cleanup).
     * Removes the empty price level if no orders remain at that price.
     */
    public void removeOrder(Order order) {
        TreeMap<BigDecimal, Deque<Order>> side = sideFor(order.getSide());
        Deque<Order> level = side.get(order.getPrice());
        if (level == null) return;

        level.remove(order);
        if (level.isEmpty()) {
            side.remove(order.getPrice());
        }
    }

    /**
     * Best bid: highest price a buyer is willing to pay.
     */
    public Optional<Map.Entry<BigDecimal, Deque<Order>>> getBestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstEntry());
    }

    /**
     * Best ask: lowest price a seller is willing to accept.
     */
    public Optional<Map.Entry<BigDecimal, Deque<Order>>> getBestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstEntry());
    }

    /**
     * Returns the queue of orders at a specific price level on a given side.
     * Returns an empty deque if no orders exist at that level.
     */
    public Deque<Order> getOrdersAt(Side side, BigDecimal price) {
        Deque<Order> level = sideFor(side).get(price);
        return level != null ? level : new ArrayDeque<>();
    }

    public TreeMap<BigDecimal, Deque<Order>> getBids() { return bids; }
    public TreeMap<BigDecimal, Deque<Order>> getAsks() { return asks; }
    public String getInstrument()                      { return instrument; }

    private TreeMap<BigDecimal, Deque<Order>> sideFor(Side side) {
        return side == Side.BUY ? bids : asks;
    }
}
