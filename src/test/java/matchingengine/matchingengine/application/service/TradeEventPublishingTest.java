package matchingengine.matchingengine.application.service;

import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderBookRepository;
import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderRepository;
import matchingengine.matchingengine.domain.MatchingEngine;
import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.Side;
import matchingengine.matchingengine.domain.events.DomainEvent;
import matchingengine.matchingengine.domain.events.TradeExecutedEvent;
import matchingengine.matchingengine.domain.ports.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradeEventPublishingTest {

    private OrderApplicationService service;
    private DomainEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(DomainEventPublisher.class);
        service = new OrderApplicationService(
                new MatchingEngine(),
                new InMemoryOrderRepository(),
                new InMemoryOrderBookRepository(),
                eventPublisher
        );
    }

    private Order buy(String price, String qty) {
        return new Order("BTC-USD", Side.BUY, new BigDecimal(price), new BigDecimal(qty));
    }

    private Order sell(String price, String qty) {
        return new Order("BTC-USD", Side.SELL, new BigDecimal(price), new BigDecimal(qty));
    }

    @Test
    void noMatch_noEventPublished() {
        service.submit(buy("100", "5"));

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void singleFill_publishesOneTradeExecutedEvent() {
        service.submit(sell("100", "5"));
        service.submit(buy("100", "5"));

        verify(eventPublisher, times(1)).publish(any(TradeExecutedEvent.class));
    }

    @Test
    void multipleFills_publishesOneEventPerFill() {
        // 3 resting sells at different price levels
        service.submit(sell("100", "1"));
        service.submit(sell("101", "1"));
        service.submit(sell("102", "1"));

        // Incoming buy sweeps all 3 levels → 3 trades → 3 events
        service.submit(buy("102", "3"));

        verify(eventPublisher, times(3)).publish(any(TradeExecutedEvent.class));
    }

    @Test
    void tradeExecutedEvent_containsCorrectTradeData() {
        List<DomainEvent> capturedEvents = new ArrayList<>();
        doAnswer(inv -> { capturedEvents.add(inv.getArgument(0)); return null; })
                .when(eventPublisher).publish(any());

        service.submit(sell("100", "5"));
        service.submit(buy("100", "5"));

        assertEquals(1, capturedEvents.size());
        assertInstanceOf(TradeExecutedEvent.class, capturedEvents.get(0));

        var event = (TradeExecutedEvent) capturedEvents.get(0);
        assertEquals(new BigDecimal("100"), event.trade().getPrice());
        assertEquals(new BigDecimal("5"), event.trade().getQuantity());
        assertEquals("BTC-USD", event.trade().getInstrument());
    }
}
