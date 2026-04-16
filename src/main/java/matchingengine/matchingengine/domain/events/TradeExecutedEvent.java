package matchingengine.matchingengine.domain.events;

import matchingengine.matchingengine.domain.Trade;

public record TradeExecutedEvent(Trade trade) implements DomainEvent {
}
