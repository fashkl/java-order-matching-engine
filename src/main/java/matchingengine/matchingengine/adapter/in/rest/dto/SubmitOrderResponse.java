package matchingengine.matchingengine.adapter.in.rest.dto;

import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.Trade;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class SubmitOrderResponse {

    private final UUID orderId;
    private final String status;
    private final BigDecimal remainingQty;
    private final List<TradeDto> trades;

    private SubmitOrderResponse(UUID orderId, String status,
                                BigDecimal remainingQty, List<TradeDto> trades) {
        this.orderId = orderId;
        this.status = status;
        this.remainingQty = remainingQty;
        this.trades = trades;
    }

    public static SubmitOrderResponse from(Order order, List<Trade> trades) {
        return new SubmitOrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getRemainingQty(),
                trades.stream().map(TradeDto::from).toList()
        );
    }

    public UUID getOrderId()            { return orderId; }
    public String getStatus()           { return status; }
    public BigDecimal getRemainingQty() { return remainingQty; }
    public List<TradeDto> getTrades()   { return trades; }
}
