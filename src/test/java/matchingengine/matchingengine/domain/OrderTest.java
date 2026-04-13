package matchingengine.matchingengine.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private Order buyOrder(String price, String qty) {
        return new Order("BTC-USD", Side.BUY, new BigDecimal(price), new BigDecimal(qty));
    }

    private Order sellOrder(String price, String qty) {
        return new Order("BTC-USD", Side.SELL, new BigDecimal(price), new BigDecimal(qty));
    }

    // --- Construction ---

    @Test
    void newOrder_hasCorrectInitialState() {
        Order order = buyOrder("100", "5");

        assertNotNull(order.getId());
        assertEquals("BTC-USD", order.getInstrument());
        assertEquals(Side.BUY, order.getSide());
        assertEquals(new BigDecimal("100"), order.getPrice());
        assertEquals(new BigDecimal("5"), order.getQuantity());
        assertEquals(new BigDecimal("5"), order.getRemainingQty());
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void constructor_rejectsZeroPrice() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("BTC-USD", Side.BUY, BigDecimal.ZERO, new BigDecimal("1")));
    }

    @Test
    void constructor_rejectsNegativePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("BTC-USD", Side.BUY, new BigDecimal("-1"), new BigDecimal("1")));
    }

    @Test
    void constructor_rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("BTC-USD", Side.BUY, new BigDecimal("100"), BigDecimal.ZERO));
    }

    @Test
    void constructor_rejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order("BTC-USD", Side.BUY, new BigDecimal("100"), new BigDecimal("-5")));
    }

    // --- fill() ---

    @Test
    void fill_partial_setsPartiallyFilledStatus() {
        Order order = buyOrder("100", "10");

        order.fill(new BigDecimal("4"));

        assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
        assertEquals(new BigDecimal("6"), order.getRemainingQty());
    }

    @Test
    void fill_full_setsFilledStatus() {
        Order order = buyOrder("100", "10");

        order.fill(new BigDecimal("10"));

        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(BigDecimal.ZERO, order.getRemainingQty());
    }

    @Test
    void fill_multiple_accumulatesCorrectly() {
        Order order = buyOrder("100", "10");

        order.fill(new BigDecimal("3"));
        order.fill(new BigDecimal("3"));
        order.fill(new BigDecimal("4"));

        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(BigDecimal.ZERO, order.getRemainingQty());
    }

    @Test
    void fill_throwsWhenExceedsRemainingQty() {
        Order order = buyOrder("100", "5");

        assertThrows(IllegalStateException.class,
                () -> order.fill(new BigDecimal("6")));
    }

    @Test
    void fill_throwsOnZeroFillQty() {
        Order order = buyOrder("100", "5");

        assertThrows(IllegalArgumentException.class,
                () -> order.fill(BigDecimal.ZERO));
    }

    @Test
    void fill_throwsOnNegativeFillQty() {
        Order order = buyOrder("100", "5");

        assertThrows(IllegalArgumentException.class,
                () -> order.fill(new BigDecimal("-1")));
    }

    // --- cancel() ---

    @Test
    void cancel_newOrder_becomesCancelled() {
        Order order = buyOrder("100", "5");

        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void cancel_partiallyFilledOrder_becomesCancelled() {
        Order order = buyOrder("100", "10");
        order.fill(new BigDecimal("4"));

        order.cancel();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void cancel_filledOrder_isNoOp() {
        Order order = buyOrder("100", "5");
        order.fill(new BigDecimal("5"));

        order.cancel(); // should not throw or change status

        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    @Test
    void cancel_alreadyCancelledOrder_isNoOp() {
        Order order = buyOrder("100", "5");
        order.cancel();

        order.cancel(); // second cancel — should be silent

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    // --- isOpen() ---

    @Test
    void isOpen_trueForNewOrder() {
        assertTrue(buyOrder("100", "5").isOpen());
    }

    @Test
    void isOpen_trueForPartiallyFilledOrder() {
        Order order = buyOrder("100", "10");
        order.fill(new BigDecimal("3"));

        assertTrue(order.isOpen());
    }

    @Test
    void isOpen_falseForFilledOrder() {
        Order order = buyOrder("100", "5");
        order.fill(new BigDecimal("5"));

        assertFalse(order.isOpen());
    }

    @Test
    void isOpen_falseForCancelledOrder() {
        Order order = buyOrder("100", "5");
        order.cancel();

        assertFalse(order.isOpen());
    }
}
