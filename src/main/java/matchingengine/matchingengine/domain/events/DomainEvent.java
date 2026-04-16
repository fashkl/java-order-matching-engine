package matchingengine.matchingengine.domain.events;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent permits TradeExecutedEvent {

    /** Unique ID for this event instance — used for deduplication on retry. */
    UUID eventId();

    /** When this event occurred — used for ordering and traceability. */
    Instant occurredAt();
}
