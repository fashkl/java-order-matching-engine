package matchingengine.matchingengine.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook("BTC-USD");
    }

    private Order buyOrder(String price, String qty) {
        return new Order("BTC-USD", Side.BUY, new BigDecimal(price), new BigDecimal(qty));
    }

    private Order sellOrder(String price, String qty) {
        return new Order("BTC-USD", Side.SELL, new BigDecimal(price), new BigDecimal(qty));
    }

    // --- addOrder ---

    @Test
    void addBuyOrder_appearsOnBidSide() {
        Order order = buyOrder("100", "5");
        book.addOrder(order);

        assertFalse(book.getBids().isEmpty());
        assertTrue(book.getAsks().isEmpty());
    }

    @Test
    void addSellOrder_appearsOnAskSide() {
        Order order = sellOrder("100", "5");
        book.addOrder(order);

        assertFalse(book.getAsks().isEmpty());
        assertTrue(book.getBids().isEmpty());
    }

    @Test
    void multipleOrdersAtSamePrice_maintainInsertionOrder() {
        Order first  = buyOrder("100", "3");
        Order second = buyOrder("100", "5");
        Order third  = buyOrder("100", "2");

        book.addOrder(first);
        book.addOrder(second);
        book.addOrder(third);

        Deque<Order> level = book.getOrdersAt(Side.BUY, new BigDecimal("100"));
        assertEquals(3, level.size());
        assertSame(first,  level.pollFirst()); // oldest first
        assertSame(second, level.pollFirst());
        assertSame(third,  level.pollFirst());
    }

    // --- getBestBid / getBestAsk ---

    @Test
    void bestBid_isHighestPrice() {
        book.addOrder(buyOrder("98", "1"));
        book.addOrder(buyOrder("100", "1"));
        book.addOrder(buyOrder("99", "1"));

        assertEquals(new BigDecimal("100"), book.getBestBid().get().getKey());
    }

    @Test
    void bestAsk_isLowestPrice() {
        book.addOrder(sellOrder("102", "1"));
        book.addOrder(sellOrder("100", "1"));
        book.addOrder(sellOrder("101", "1"));

        assertEquals(new BigDecimal("100"), book.getBestAsk().get().getKey());
    }

    @Test
    void bestBid_emptyBook_returnsEmpty() {
        assertTrue(book.getBestBid().isEmpty());
    }

    @Test
    void bestAsk_emptyBook_returnsEmpty() {
        assertTrue(book.getBestAsk().isEmpty());
    }

    // --- removeOrder ---

    @Test
    void removeOrder_decreasesLevelSize() {
        Order order = buyOrder("100", "5");
        book.addOrder(order);
        book.addOrder(buyOrder("100", "3"));

        book.removeOrder(order);

        assertEquals(1, book.getOrdersAt(Side.BUY, new BigDecimal("100")).size());
    }

    @Test
    void removeLastOrderAtLevel_removesThePriceLevel() {
        Order order = buyOrder("100", "5");
        book.addOrder(order);

        book.removeOrder(order);

        assertFalse(book.getBids().containsKey(new BigDecimal("100")));
        assertTrue(book.getBestBid().isEmpty());
    }

    @Test
    void removeOrder_unknownOrder_isNoOp() {
        Order unknown = buyOrder("100", "5"); // never added

        assertDoesNotThrow(() -> book.removeOrder(unknown));
    }

    @Test
    void removeOrder_updatesNextBestBid() {
        book.addOrder(buyOrder("100", "1"));
        Order top = buyOrder("102", "1");
        book.addOrder(top);

        book.removeOrder(top);

        assertEquals(new BigDecimal("100"), book.getBestBid().get().getKey());
    }
}
