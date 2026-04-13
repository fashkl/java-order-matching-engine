package matchingengine.matchingengine.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MatchingEngine {

    /**
     * Match an incoming order against the opposite side of the book.
     *
     * Returns a list of trades produced by this order.
     * The incoming order is added to the book if it has remaining qty after matching.
     */
    public List<Trade> match(Order incoming, OrderBook book) {
        List<Trade> trades = new ArrayList<>();

        if (incoming.getSide() == Side.BUY) {
            matchBuy(incoming, book, trades);
        } else {
            matchSell(incoming, book, trades);
        }

        // If the incoming order still has remaining qty, it rests on the book
        if (incoming.isOpen()) {
            book.addOrder(incoming);
        }

        return trades;
    }

    // --- Private ---

    private void matchBuy(Order incoming, OrderBook book, List<Trade> trades) {
        // Walk asks from lowest price upward
        // Match while best ask price <= incoming buy price
        while (incoming.isOpen()) {
            var bestAskEntry = book.getBestAsk();
            if (bestAskEntry.isEmpty()) break;

            Map.Entry<BigDecimal, Deque<Order>> entry = bestAskEntry.get();
            BigDecimal askPrice = entry.getKey();

            if (askPrice.compareTo(incoming.getPrice()) > 0) break; // prices don't cross

            fillLevel(incoming, entry.getValue(), askPrice, book, trades);
        }
    }

    private void matchSell(Order incoming, OrderBook book, List<Trade> trades) {
        // Walk bids from highest price downward
        // Match while best bid price >= incoming sell price
        while (incoming.isOpen()) {
            var bestBidEntry = book.getBestBid();
            if (bestBidEntry.isEmpty()) break;

            Map.Entry<BigDecimal, Deque<Order>> entry = bestBidEntry.get();
            BigDecimal bidPrice = entry.getKey();

            if (bidPrice.compareTo(incoming.getPrice()) < 0) break; // prices don't cross

            fillLevel(incoming, entry.getValue(), bidPrice, book, trades);
        }
    }

    /**
     * Fill the incoming order against all resting orders at a single price level.
     * Removes fully filled resting orders from the book.
     */
    private void fillLevel(Order incoming, Deque<Order> level,
                           BigDecimal restingPrice, OrderBook book, List<Trade> trades) {
        while (incoming.isOpen() && !level.isEmpty()) {
            Order resting = level.peekFirst();

            BigDecimal fillQty = incoming.getRemainingQty()
                    .min(resting.getRemainingQty());

            incoming.fill(fillQty);
            resting.fill(fillQty);

            Trade trade = buildTrade(incoming, resting, restingPrice, fillQty);
            trades.add(trade);

            if (!resting.isOpen()) {
                book.removeOrder(resting); // fully filled — off the book
            }
        }
    }

    private Trade buildTrade(Order incoming, Order resting,
                             BigDecimal price, BigDecimal qty) {
        UUID buyId  = incoming.getSide() == Side.BUY ? incoming.getId() : resting.getId();
        UUID sellId = incoming.getSide() == Side.SELL ? incoming.getId() : resting.getId();
        return new Trade(incoming.getInstrument(), buyId, sellId, price, qty);
    }
}
