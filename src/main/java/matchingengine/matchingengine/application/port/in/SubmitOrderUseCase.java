package matchingengine.matchingengine.application.port.in;

import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.Trade;

import java.util.List;

public interface SubmitOrderUseCase {

    /**
     * Submit a limit order for matching.
     * Returns all trades produced immediately by this order.
     * Any unfilled remainder rests on the order book.
     */
    List<Trade> submit(Order order);
}
