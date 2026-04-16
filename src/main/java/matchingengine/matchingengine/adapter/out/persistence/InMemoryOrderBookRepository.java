package matchingengine.matchingengine.adapter.out.persistence;

import matchingengine.matchingengine.application.port.out.OrderBookRepositoryPort;
import matchingengine.matchingengine.domain.OrderBook;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrderBookRepository implements OrderBookRepositoryPort {

    private final Map<String, OrderBook> store = new ConcurrentHashMap<>();

    @Override
    public OrderBook findOrCreate(String instrument) {
        return store.computeIfAbsent(instrument, OrderBook::new);
    }

    @Override
    public Optional<OrderBook> findByInstrument(String instrument) {
        return Optional.ofNullable(store.get(instrument));
    }
}
