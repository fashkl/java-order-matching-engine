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
import matchingengine.matchingengine.domain.events.TradeExecutedEvent;
import matchingengine.matchingengine.domain.ports.DomainEventPublisher;
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
    private final DomainEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public OrderApplicationService(MatchingEngine matchingEngine,
                                   OrderRepositoryPort orderRepository,
                                   OrderBookRepositoryPort orderBookRepository,
                                   DomainEventPublisher eventPublisher) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
        this.orderBookRepository = orderBookRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Trade> submit(Order order) {
        List<Trade> trades;

        ReentrantLock lock = lockFor(order.getInstrument());
        lock.lock();
        try {
            orderRepository.save(order);
            OrderBook book = orderBookRepository.findOrCreate(order.getInstrument());
            trades = matchingEngine.match(order, book);
        } finally {
            lock.unlock();
        }

        // Publish events after releasing the lock — no I/O inside the critical section
        trades.forEach(trade -> eventPublisher.publish(TradeExecutedEvent.of(trade)));

        return trades;
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
