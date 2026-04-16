package matchingengine.matchingengine.adapter.out.eventbus;

import matchingengine.matchingengine.domain.events.DomainEvent;
import matchingengine.matchingengine.domain.ports.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
