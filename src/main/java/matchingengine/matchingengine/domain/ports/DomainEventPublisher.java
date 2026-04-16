package matchingengine.matchingengine.domain.ports;

import matchingengine.matchingengine.domain.events.DomainEvent;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
