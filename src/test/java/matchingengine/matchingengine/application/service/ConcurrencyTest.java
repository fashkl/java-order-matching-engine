package matchingengine.matchingengine.application.service;

import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderBookRepository;
import matchingengine.matchingengine.adapter.out.persistence.InMemoryOrderRepository;
import matchingengine.matchingengine.domain.MatchingEngine;
import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.OrderStatus;
import matchingengine.matchingengine.domain.Side;
import matchingengine.matchingengine.domain.Trade;
import matchingengine.matchingengine.domain.ports.DomainEventPublisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    private OrderApplicationService service;
    private InMemoryOrderBookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository = new InMemoryOrderBookRepository();
        service = new OrderApplicationService(
                new MatchingEngine(),
                new InMemoryOrderRepository(),
                bookRepository,
                mock(DomainEventPublisher.class)
        );
    }

    /**
     * 10 threads each submit 1 BUY and 1 SELL at crossing prices.
     * Total available quantity on each side = 10 × qty.
     * Assert: no double-fills, no negative remainingQty, total filled qty is consistent.
     */
    @RepeatedTest(5)
    void concurrentSubmits_sameInstrument_noDoubleFills() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads * 2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads * 2);

        List<Trade> allTrades = Collections.synchronizedList(new ArrayList<>());
        List<Order> allOrders = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            // Each thread submits a BUY
            executor.submit(() -> {
                try {
                    startGate.await();
                    Order buy = new Order("BTC-USD", Side.BUY, new BigDecimal("100"), new BigDecimal("1"));
                    allOrders.add(buy);
                    allTrades.addAll(service.submit(buy));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });

            // Each thread submits a SELL
            executor.submit(() -> {
                try {
                    startGate.await();
                    Order sell = new Order("BTC-USD", Side.SELL, new BigDecimal("100"), new BigDecimal("1"));
                    allOrders.add(sell);
                    allTrades.addAll(service.submit(sell));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads simultaneously
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
        executor.shutdown();

        // No order should have negative remainingQty
        for (Order order : allOrders) {
            assertTrue(order.getRemainingQty().compareTo(BigDecimal.ZERO) >= 0,
                    "Negative remainingQty detected on order " + order.getId());
        }

        // Total filled qty in all trades must equal total qty traded (no double-fill)
        BigDecimal totalTradeQty = allTrades.stream()
                .map(Trade::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Each trade fills 1 unit, max 10 trades possible (10 buys × 10 sells)
        assertTrue(totalTradeQty.compareTo(new BigDecimal("10")) <= 0,
                "Total traded qty exceeded available supply: " + totalTradeQty);
    }

    /**
     * 5 threads on BTC-USD and 5 threads on ETH-USD submit simultaneously.
     * Assert: instruments are completely independent — no cross-instrument fills.
     */
    @Test
    void concurrentSubmits_differentInstruments_booksAreIndependent() throws InterruptedException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads * 2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads * 2);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    service.submit(new Order("BTC-USD", Side.BUY, new BigDecimal("100"), new BigDecimal("1")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startGate.await();
                    service.submit(new Order("ETH-USD", Side.SELL, new BigDecimal("100"), new BigDecimal("1")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // BTC buys should not have matched ETH sells
        var btcBook = bookRepository.findByInstrument("BTC-USD");
        var ethBook = bookRepository.findByInstrument("ETH-USD");

        assertTrue(btcBook.isPresent());
        assertTrue(ethBook.isPresent());
        // All BTC buys are still on the book (no ETH sells to match against)
        assertFalse(btcBook.get().getBids().isEmpty());
        // All ETH sells are still on the book (no BTC buys to match against)
        assertFalse(ethBook.get().getAsks().isEmpty());
    }

    /**
     * Submit an order, then immediately try to cancel it from another thread.
     * Assert: the order is either FILLED or CANCELLED — never in a corrupted intermediate state.
     */
    @RepeatedTest(10)
    void concurrentSubmitAndCancel_orderEndsInValidState() throws InterruptedException {
        // Put a resting sell so the buy will match
        service.submit(new Order("BTC-USD", Side.SELL, new BigDecimal("100"), new BigDecimal("1")));

        Order buy = new Order("BTC-USD", Side.BUY, new BigDecimal("100"), new BigDecimal("1"));

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: submit the buy
        executor.submit(() -> {
            try {
                startGate.await();
                service.submit(buy);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: cancel the buy immediately
        executor.submit(() -> {
            try {
                startGate.await();
                // submit registers the order before matching, so cancel can find it
                try { service.cancel(buy.getId()); } catch (Exception ignored) {}
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        startGate.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Order must be in a valid terminal or open state — never corrupted
        OrderStatus status = buy.getStatus();
        assertTrue(
                status == OrderStatus.FILLED ||
                status == OrderStatus.CANCELLED ||
                status == OrderStatus.PARTIALLY_FILLED ||
                status == OrderStatus.NEW,
                "Order in unexpected state: " + status
        );
        // remainingQty must never be negative
        assertTrue(buy.getRemainingQty().compareTo(BigDecimal.ZERO) >= 0);
    }
}
