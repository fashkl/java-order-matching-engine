package matchingengine.matchingengine.adapter.out.persistence;

import matchingengine.matchingengine.application.port.out.OrderBookRepositoryPort;
import matchingengine.matchingengine.domain.OrderBook;

import java.util.HashMap;
import java.util.Map;

public class InMemoryOrderBookRepository implements OrderBookRepositoryPort {

    private final Map<String, OrderBook> store = new HashMap<>();

    @Override
    public OrderBook findOrCreate(String instrument) {
        return store.computeIfAbsent(instrument, OrderBook::new);
    }
}
