package matchingengine.matchingengine.application.service;

import matchingengine.matchingengine.application.port.in.CancelOrderUseCase;
import matchingengine.matchingengine.application.port.in.SubmitOrderUseCase;
import matchingengine.matchingengine.application.port.out.OrderBookRepositoryPort;
import matchingengine.matchingengine.application.port.out.OrderRepositoryPort;
import matchingengine.matchingengine.domain.MatchingEngine;
import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.OrderBook;
import matchingengine.matchingengine.domain.Trade;
import matchingengine.matchingengine.exception.OrderNotFoundException;

import java.util.List;
import java.util.UUID;

public class OrderApplicationService implements SubmitOrderUseCase, CancelOrderUseCase {

    private final MatchingEngine matchingEngine;
    private final OrderRepositoryPort orderRepository;
    private final OrderBookRepositoryPort orderBookRepository;

    public OrderApplicationService(MatchingEngine matchingEngine,
                                   OrderRepositoryPort orderRepository,
                                   OrderBookRepositoryPort orderBookRepository) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
        this.orderBookRepository = orderBookRepository;
    }

    @Override
    public List<Trade> submit(Order order) {
        orderRepository.save(order);
        OrderBook book = orderBookRepository.findOrCreate(order.getInstrument());
        return matchingEngine.match(order, book);
    }

    @Override
    public Order cancel(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean wasOpen = order.isOpen();
        order.cancel();

        if (wasOpen) {
            OrderBook book = orderBookRepository.findOrCreate(order.getInstrument());
            book.removeOrder(order);
        }

        return order;
    }
}
