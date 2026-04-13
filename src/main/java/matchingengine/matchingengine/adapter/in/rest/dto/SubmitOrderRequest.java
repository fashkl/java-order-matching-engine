package matchingengine.matchingengine.adapter.in.rest.dto;

import java.math.BigDecimal;

public class SubmitOrderRequest {

    private String instrument;
    private String side;        // "BUY" or "SELL"
    private BigDecimal price;
    private BigDecimal quantity;

    public String getInstrument()    { return instrument; }
    public String getSide()          { return side; }
    public BigDecimal getPrice()     { return price; }
    public BigDecimal getQuantity()  { return quantity; }

    public void setInstrument(String instrument)   { this.instrument = instrument; }
    public void setSide(String side)               { this.side = side; }
    public void setPrice(BigDecimal price)         { this.price = price; }
    public void setQuantity(BigDecimal quantity)   { this.quantity = quantity; }
}
