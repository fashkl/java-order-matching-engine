package matchingengine.matchingengine.application.port.in;

import matchingengine.matchingengine.domain.Order;

import java.util.UUID;

public interface CancelOrderUseCase {

    /**
     * Cancel an open order by ID.
     * Throws OrderNotFoundException if the ID is unknown.
     * Idempotent — cancelling an already filled or cancelled order is a no-op.
     * Returns the order after cancellation.
     */
    Order cancel(UUID orderId);
}
