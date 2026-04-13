package matchingengine.matchingengine.application.port.out;

import matchingengine.matchingengine.domain.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

    void save(Order order);

    Optional<Order> findById(UUID orderId);
}
