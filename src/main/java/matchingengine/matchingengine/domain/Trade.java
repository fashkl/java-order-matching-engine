package matchingengine.matchingengine.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Trade {

    private final UUID id;
    private final String instrument;
    private final UUID buyOrderId;
    private final UUID sellOrderId;
    private final BigDecimal price;     // resting order's price (passive side sets the price)
    private final BigDecimal quantity;  // amount exchanged in this fill
    private final Instant executedAt;

    public Trade(String instrument, UUID buyOrderId, UUID sellOrderId,
                 BigDecimal price, BigDecimal quantity) {
        this.id = UUID.randomUUID();
        this.instrument = instrument;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = Instant.now();
    }

    public UUID getId()            { return id; }
    public String getInstrument()  { return instrument; }
    public UUID getBuyOrderId()    { return buyOrderId; }
    public UUID getSellOrderId()   { return sellOrderId; }
    public BigDecimal getPrice()   { return price; }
    public BigDecimal getQuantity(){ return quantity; }
    public Instant getExecutedAt() { return executedAt; }

    @Override
    public String toString() {
        return String.format("Trade{id=%s, instrument=%s, buy=%s, sell=%s, price=%s, qty=%s}",
                id, instrument, buyOrderId, sellOrderId, price, quantity);
    }
}
