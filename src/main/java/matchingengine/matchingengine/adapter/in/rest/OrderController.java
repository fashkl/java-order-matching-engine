package matchingengine.matchingengine.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Order submission, cancellation, and market data")
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

    @Operation(
            summary = "Submit a limit order",
            description = "Places a BUY or SELL limit order. Returns the order status and any immediate fills.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order accepted",
                            content = @Content(schema = @Schema(implementation = SubmitOrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (negative price, zero quantity, unknown side)")
            }
    )
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

    @Operation(
            summary = "Cancel an order",
            description = "Cancels an open order by ID. Idempotent — cancelling a filled or already cancelled order is a no-op.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Order cancelled"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> cancel(
            @Parameter(description = "Order ID to cancel") @PathVariable UUID id) {
        cancelOrder.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get order book",
            description = "Returns all current bids and asks for an instrument, grouped by price level.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order book returned",
                            content = @Content(schema = @Schema(implementation = OrderBookResponse.class))),
                    @ApiResponse(responseCode = "404", description = "No orders exist for this instrument")
            }
    )
    @GetMapping("/orderbook/{instrument}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @Parameter(description = "Instrument symbol, e.g. BTC-USD") @PathVariable String instrument) {
        return getOrderBook.getOrderBook(instrument)
                .map(book -> ResponseEntity.ok(OrderBookResponse.from(book)))
                .orElse(ResponseEntity.notFound().build());
    }
}
