package matchingengine.matchingengine.adapter.in.rest.dto;

import matchingengine.matchingengine.domain.Trade;

import java.math.BigDecimal;
import java.util.UUID;

public class TradeDto {

    private final UUID tradeId;
    private final BigDecimal price;
    private final BigDecimal quantity;

    private TradeDto(UUID tradeId, BigDecimal price, BigDecimal quantity) {
        this.tradeId = tradeId;
        this.price = price;
        this.quantity = quantity;
    }

    public static TradeDto from(Trade trade) {
        return new TradeDto(trade.getId(), trade.getPrice(), trade.getQuantity());
    }

    public UUID getTradeId()         { return tradeId; }
    public BigDecimal getPrice()     { return price; }
    public BigDecimal getQuantity()  { return quantity; }
}
