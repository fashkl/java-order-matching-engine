package matchingengine.matchingengine.adapter.out.persistence;

import matchingengine.matchingengine.application.port.out.OrderRepositoryPort;
import matchingengine.matchingengine.domain.Order;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryOrderRepository implements OrderRepositoryPort {

    private final Map<UUID, Order> store = new HashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return Optional.ofNullable(store.get(orderId));
    }
}
