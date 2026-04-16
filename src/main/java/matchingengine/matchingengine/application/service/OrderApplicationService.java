package matchingengine.matchingengine.application.service;

import matchingengine.matchingengine.application.port.in.CancelOrderUseCase;
import matchingengine.matchingengine.application.port.in.GetOrderBookUseCase;
import matchingengine.matchingengine.application.port.in.SubmitOrderUseCase;
import matchingengine.matchingengine.application.port.out.OrderBookRepositoryPort;
import matchingengine.matchingengine.application.port.out.OrderRepositoryPort;
import matchingengine.matchingengine.domain.MatchingEngine;
import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.OrderBook;
import matchingengine.matchingengine.domain.Trade;
import matchingengine.matchingengine.exception.OrderNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class OrderApplicationService implements SubmitOrderUseCase, CancelOrderUseCase, GetOrderBookUseCase {

    private final MatchingEngine matchingEngine;
    private final OrderRepositoryPort orderRepository;
    private final OrderBookRepositoryPort orderBookRepository;

    // One lock per instrument — different instruments never contend on each other
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public OrderApplicationService(MatchingEngine matchingEngine,
                                   OrderRepositoryPort orderRepository,
                                   OrderBookRepositoryPort orderBookRepository) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
        this.orderBookRepository = orderBookRepository;
    }

    @Override
    public List<Trade> submit(Order order) {
        ReentrantLock lock = lockFor(order.getInstrument());
        lock.lock();
        try {
            orderRepository.save(order);
            OrderBook book = orderBookRepository.findOrCreate(order.getInstrument());
            return matchingEngine.match(order, book);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Order cancel(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        ReentrantLock lock = lockFor(order.getInstrument());
        lock.lock();
        try {
            boolean wasOpen = order.isOpen();
            order.cancel();

            if (wasOpen) {
                OrderBook book = orderBookRepository.findOrCreate(order.getInstrument());
                book.removeOrder(order);
            }
        } finally {
            lock.unlock();
        }

        return order;
    }

    @Override
    public Optional<OrderBook> getOrderBook(String instrument) {
        return orderBookRepository.findByInstrument(instrument);
    }

    private ReentrantLock lockFor(String instrument) {
        return locks.computeIfAbsent(instrument, k -> new ReentrantLock());
    }
}
