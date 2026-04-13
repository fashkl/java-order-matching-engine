package matchingengine.matchingengine.application.port.in;

import matchingengine.matchingengine.domain.OrderBook;
import java.util.Optional;

public interface GetOrderBookUseCase {

    /**
     * Returns the live order book for the given instrument,
     * or empty if no orders have been submitted for it yet.
     */
    Optional<OrderBook> getOrderBook(String instrument);
}
