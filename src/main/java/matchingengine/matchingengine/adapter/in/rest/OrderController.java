package matchingengine.matchingengine.adapter.in.rest;

import matchingengine.matchingengine.adapter.in.rest.dto.OrderBookResponse;
import matchingengine.matchingengine.adapter.in.rest.dto.SubmitOrderRequest;
import matchingengine.matchingengine.adapter.in.rest.dto.SubmitOrderResponse;
import matchingengine.matchingengine.application.port.in.CancelOrderUseCase;
import matchingengine.matchingengine.application.port.in.GetOrderBookUseCase;
import matchingengine.matchingengine.application.port.in.SubmitOrderUseCase;
import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.Side;
import matchingengine.matchingengine.domain.Trade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final SubmitOrderUseCase submitOrder;
    private final CancelOrderUseCase cancelOrder;
    private final GetOrderBookUseCase getOrderBook;

    public OrderController(SubmitOrderUseCase submitOrder,
                           CancelOrderUseCase cancelOrder,
                           GetOrderBookUseCase getOrderBook) {
        this.submitOrder = submitOrder;
        this.cancelOrder = cancelOrder;
        this.getOrderBook = getOrderBook;
    }

    @PostMapping("/orders")
    public ResponseEntity<SubmitOrderResponse> submit(@RequestBody SubmitOrderRequest request) {
        Order order = new Order(
                request.getInstrument(),
                Side.valueOf(request.getSide()),
                request.getPrice(),
                request.getQuantity()
        );
        List<Trade> trades = submitOrder.submit(order);
        return ResponseEntity.ok(SubmitOrderResponse.from(order, trades));
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        cancelOrder.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orderbook/{instrument}")
    public ResponseEntity<OrderBookResponse> getOrderBook(@PathVariable String instrument) {
        return getOrderBook.getOrderBook(instrument)
                .map(book -> ResponseEntity.ok(OrderBookResponse.from(book)))
                .orElse(ResponseEntity.notFound().build());
    }
}
