package matchingengine.matchingengine.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private MatchingEngine engine;
    private OrderBook book;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
        book = new OrderBook("BTC-USD");
    }

    private Order buy(String price, String qty) {
        return new Order("BTC-USD", Side.BUY, new BigDecimal(price), new BigDecimal(qty));
    }

    private Order sell(String price, String qty) {
        return new Order("BTC-USD", Side.SELL, new BigDecimal(price), new BigDecimal(qty));
    }

    // --- No match ---

    @Test
    void noMatch_buyPriceBelowAsk_orderRestsOnBook() {
        book.addOrder(sell("102", "5"));

        Order incoming = buy("100", "3");
        List<Trade> trades = engine.match(incoming, book);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.NEW, incoming.getStatus());
        // incoming rests on bid side
        assertEquals(1, book.getBids().size());
    }

    @Test
    void noMatch_emptyBook_orderRestsOnBook() {
        Order incoming = buy("100", "3");
        List<Trade> trades = engine.match(incoming, book);

        assertTrue(trades.isEmpty());
        assertEquals(1, book.getBids().size());
    }

    // --- Full fill ---

    @Test
    void fullFill_exactQtyMatch_producesSingleTrade() {
        Order resting = sell("100", "5");
        book.addOrder(resting);

        Order incoming = buy("100", "5");
        List<Trade> trades = engine.match(incoming, book);

        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("5"), trades.get(0).getQuantity());
        assertEquals(new BigDecimal("100"), trades.get(0).getPrice());

        assertEquals(OrderStatus.FILLED, incoming.getStatus());
        assertEquals(OrderStatus.FILLED, resting.getStatus());
        assertTrue(book.getAsks().isEmpty()); // resting removed from book
        assertTrue(book.getBids().isEmpty()); // incoming fully filled, not added
    }

    @Test
    void fullFill_buyPriceAboveAsk_executesAtAskPrice() {
        // Buyer willing to pay $105, seller asks $100 → trade at $100 (resting price)
        book.addOrder(sell("100", "3"));

        Order incoming = buy("105", "3");
        List<Trade> trades = engine.match(incoming, book);

        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("100"), trades.get(0).getPrice()); // resting price
        assertEquals(OrderStatus.FILLED, incoming.getStatus());
    }

    // --- Partial fill ---

    @Test
    void partialFill_incomingLargerThanResting_remainderRestsOnBook() {
        book.addOrder(sell("100", "3"));

        Order incoming = buy("100", "5");
        List<Trade> trades = engine.match(incoming, book);

        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("3"), trades.get(0).getQuantity());

        assertEquals(OrderStatus.PARTIALLY_FILLED, incoming.getStatus());
        assertEquals(new BigDecimal("2"), incoming.getRemainingQty());
        assertTrue(book.getAsks().isEmpty());        // resting fully consumed
        assertEquals(1, book.getBids().size());      // remainder rests on bid side
    }

    @Test
    void partialFill_incomingSmallerThanResting_restingStaysOnBook() {
        Order resting = sell("100", "10");
        book.addOrder(resting);

        Order incoming = buy("100", "3");
        List<Trade> trades = engine.match(incoming, book);

        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("3"), trades.get(0).getQuantity());

        assertEquals(OrderStatus.FILLED, incoming.getStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, resting.getStatus());
        assertEquals(new BigDecimal("7"), resting.getRemainingQty());
        assertEquals(1, book.getAsks().size()); // resting still on book
    }

    // --- Multiple fills ---

    @Test
    void multipleFills_incomingSwepsMultiplePriceLevels() {
        book.addOrder(sell("100", "2"));
        book.addOrder(sell("101", "2"));
        book.addOrder(sell("102", "2"));

        Order incoming = buy("102", "6");
        List<Trade> trades = engine.match(incoming, book);

        assertEquals(3, trades.size());
        assertEquals(new BigDecimal("100"), trades.get(0).getPrice());
        assertEquals(new BigDecimal("101"), trades.get(1).getPrice());
        assertEquals(new BigDecimal("102"), trades.get(2).getPrice());

        assertEquals(OrderStatus.FILLED, incoming.getStatus());
        assertTrue(book.getAsks().isEmpty());
    }

    @Test
    void multipleFills_timePriority_oldestRestingOrderFilledFirst() {
        Order first  = sell("100", "2"); // arrives first
        Order second = sell("100", "2"); // arrives second — same price

        book.addOrder(first);
        book.addOrder(second);

        Order incoming = buy("100", "2");
        engine.match(incoming, book);

        assertEquals(OrderStatus.FILLED, first.getStatus());   // first filled
        assertEquals(OrderStatus.NEW, second.getStatus());     // second untouched
    }

    // --- Sell side matching ---

    @Test
    void sellOrder_matchesAgainstBids() {
        Order resting = buy("100", "5");
        book.addOrder(resting);

        Order incoming = sell("100", "5");
        List<Trade> trades = engine.match(incoming, book);

        assertEquals(1, trades.size());
        assertEquals(OrderStatus.FILLED, incoming.getStatus());
        assertEquals(OrderStatus.FILLED, resting.getStatus());
        assertTrue(book.getBids().isEmpty());
    }

    @Test
    void sellOrder_noMatch_priceAboveBid_restsOnBook() {
        book.addOrder(buy("98", "5"));

        Order incoming = sell("100", "3");
        List<Trade> trades = engine.match(incoming, book);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.NEW, incoming.getStatus());
        assertEquals(1, book.getAsks().size());
    }

    // --- Trade fields ---

    @Test
    void trade_hasCorrectBuyAndSellOrderIds() {
        Order restingSell = sell("100", "5");
        book.addOrder(restingSell);

        Order incomingBuy = buy("100", "5");
        List<Trade> trades = engine.match(incomingBuy, book);

        assertEquals(1, trades.size());
        assertEquals(incomingBuy.getId(), trades.get(0).getBuyOrderId());
        assertEquals(restingSell.getId(), trades.get(0).getSellOrderId());
    }
}
