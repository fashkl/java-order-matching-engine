package matchingengine.matchingengine.application.service;

import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderBookRepository;
import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderRepository;
import matchingengine.matchingengine.domain.MatchingEngine;
import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.OrderStatus;
import matchingengine.matchingengine.domain.Side;
import matchingengine.matchingengine.domain.Trade;
import matchingengine.matchingengine.exception.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderApplicationServiceTest {

    private OrderApplicationService service;
    private InMemoryOrderBookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository = new InMemoryOrderBookRepository();
        service = new OrderApplicationService(
                new MatchingEngine(),
                new InMemoryOrderRepository(),
                bookRepository
        );
    }

    private Order buy(String price, String qty) {
        return new Order("BTC-USD", Side.BUY, new BigDecimal(price), new BigDecimal(qty));
    }

    private Order sell(String price, String qty) {
        return new Order("BTC-USD", Side.SELL, new BigDecimal(price), new BigDecimal(qty));
    }

    // --- submit ---

    @Test
    void submit_noMatch_orderRestsOnBook() {
        Order order = buy("100", "5");
        List<Trade> trades = service.submit(order);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertNotNull(bookRepository.findOrCreate("BTC-USD"));
    }

    @Test
    void submit_crossingOrders_producesTrade() {
        service.submit(sell("100", "5"));

        Order incoming = buy("100", "5");
        List<Trade> trades = service.submit(incoming);

        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("5"), trades.get(0).getQuantity());
        assertEquals(OrderStatus.FILLED, incoming.getStatus());
    }

    @Test
    void submit_partialFill_remainderRestsOnBook() {
        service.submit(sell("100", "3"));

        Order incoming = buy("100", "5");
        service.submit(incoming);

        assertEquals(OrderStatus.PARTIALLY_FILLED, incoming.getStatus());
        assertEquals(new BigDecimal("2"), incoming.getRemainingQty());
        assertEquals(1, bookRepository.findOrCreate("BTC-USD").getBids().size());
    }

    @Test
    void submit_differentInstruments_booksAreIndependent() {
        Order btcSell = new Order("BTC-USD", Side.SELL, new BigDecimal("100"), new BigDecimal("1"));
        Order ethBuy  = new Order("ETH-USD", Side.BUY,  new BigDecimal("100"), new BigDecimal("1"));

        service.submit(btcSell);
        List<Trade> trades = service.submit(ethBuy);

        assertTrue(trades.isEmpty());
    }

    // --- cancel ---

    @Test
    void cancel_openOrder_removedFromBook() {
        Order order = buy("100", "5");
        service.submit(order);

        service.cancel(order.getId());

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(bookRepository.findOrCreate("BTC-USD").getBids().isEmpty());
    }

    @Test
    void cancel_partiallyFilledOrder_removedFromBook() {
        service.submit(sell("100", "2"));

        Order order = buy("100", "5");
        service.submit(order);

        service.cancel(order.getId());

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(bookRepository.findOrCreate("BTC-USD").getBids().isEmpty());
    }

    @Test
    void cancel_filledOrder_isNoOp() {
        service.submit(sell("100", "5"));
        Order order = buy("100", "5");
        service.submit(order);

        assertDoesNotThrow(() -> service.cancel(order.getId()));
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    @Test
    void cancel_unknownOrderId_throwsOrderNotFoundException() {
        assertThrows(OrderNotFoundException.class, () -> service.cancel(UUID.randomUUID()));
    }

    @Test
    void cancel_twice_isIdempotent() {
        Order order = buy("100", "5");
        service.submit(order);

        service.cancel(order.getId());
        assertDoesNotThrow(() -> service.cancel(order.getId()));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }
}
