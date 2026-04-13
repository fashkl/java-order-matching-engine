package matchingengine.matchingengine.application.port.out;

import matchingengine.matchingengine.domain.OrderBook;

public interface OrderBookRepositoryPort {

    /**
     * Returns the order book for the given instrument.
     * Creates and stores a new one if it does not exist yet.
     */
    OrderBook findOrCreate(String instrument);
}
