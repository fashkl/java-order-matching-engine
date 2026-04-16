package matchingengine.matchingengine.domain.events;

import matchingengine.matchingengine.domain.Trade;

import java.time.Instant;
import java.util.UUID;

public record TradeExecutedEvent(
        UUID eventId,
        Instant occurredAt,
        Trade trade
) implements DomainEvent {

    /**
     * Factory method — captures metadata at the moment the event is created.
     */
    public static TradeExecutedEvent of(Trade trade) {
        return new TradeExecutedEvent(UUID.randomUUID(), Instant.now(), trade);
    }
}
