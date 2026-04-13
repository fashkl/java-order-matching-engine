package matchingengine.matchingengine.application.port.out;

import matchingengine.matchingengine.domain.OrderBook;
import java.util.Optional;

public interface OrderBookRepositoryPort {

    /**
     * Returns the order book for the given instrument.
     * Creates and stores a new one if it does not exist yet.
     */
    OrderBook findOrCreate(String instrument);

    /**
     * Returns the order book only if it already exists.
     * Does not create a new one.
     */
    Optional<OrderBook> findByInstrument(String instrument);
}
