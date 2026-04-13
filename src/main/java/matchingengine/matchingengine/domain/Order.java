package matchingengine.matchingengine.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Order {

    private final UUID id;
    private final String instrument;
    private final Side side;
    private final BigDecimal price;
    private final BigDecimal quantity;       // original, never changes
    private BigDecimal remainingQty;         // decreases as fills happen
    private OrderStatus status;
    private final Instant createdAt;

    public Order(String instrument, Side side, BigDecimal price, BigDecimal quantity) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.id = UUID.randomUUID();
        this.instrument = instrument;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.remainingQty = quantity;
        this.status = OrderStatus.NEW;
        this.createdAt = Instant.now();
    }

    /**
     * Reduce remainingQty by the filled amount and update status accordingly.
     * Called by the matching engine on each fill.
     */
    public void fill(BigDecimal filledQty) {
        if (filledQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Filled quantity must be positive");
        }
        if (filledQty.compareTo(remainingQty) > 0) {
            throw new IllegalStateException("Fill exceeds remaining quantity");
        }
        remainingQty = remainingQty.subtract(filledQty);
        status = remainingQty.compareTo(BigDecimal.ZERO) == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        if (status == OrderStatus.FILLED || status == OrderStatus.CANCELLED) {
            return; // idempotent — no-op
        }
        status = OrderStatus.CANCELLED;
    }

    public boolean isOpen() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

    // --- Getters ---

    public UUID getId()              { return id; }
    public String getInstrument()    { return instrument; }
    public Side getSide()            { return side; }
    public BigDecimal getPrice()     { return price; }
    public BigDecimal getQuantity()  { return quantity; }
    public BigDecimal getRemainingQty() { return remainingQty; }
    public OrderStatus getStatus()   { return status; }
    public Instant getCreatedAt()    { return createdAt; }

    @Override
    public String toString() {
        return String.format("Order{id=%s, instrument=%s, side=%s, price=%s, qty=%s, remaining=%s, status=%s}",
                id, instrument, side, price, quantity, remainingQty, status);
    }
}
