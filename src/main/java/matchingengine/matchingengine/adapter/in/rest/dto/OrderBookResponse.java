package matchingengine.matchingengine.adapter.in.rest.dto;

import matchingengine.matchingengine.domain.Order;
import matchingengine.matchingengine.domain.OrderBook;

import java.math.BigDecimal;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class OrderBookResponse {

    private final String instrument;
    private final List<PriceLevelDto> bids;
    private final List<PriceLevelDto> asks;

    private OrderBookResponse(String instrument,
                              List<PriceLevelDto> bids,
                              List<PriceLevelDto> asks) {
        this.instrument = instrument;
        this.bids = bids;
        this.asks = asks;
    }

    public static OrderBookResponse from(OrderBook book) {
        return new OrderBookResponse(
                book.getInstrument(),
                toLevels(book.getBids()),
                toLevels(book.getAsks())
        );
    }

    private static List<PriceLevelDto> toLevels(Map<BigDecimal, Deque<Order>> side) {
        return side.entrySet().stream()
                .map(e -> PriceLevelDto.from(e.getKey(), e.getValue()))
                .toList();
    }

    public String getInstrument()        { return instrument; }
    public List<PriceLevelDto> getBids() { return bids; }
    public List<PriceLevelDto> getAsks() { return asks; }

    public static class PriceLevelDto {
        private final BigDecimal price;
        private final BigDecimal totalQuantity;
        private final int orderCount;

        private PriceLevelDto(BigDecimal price, BigDecimal totalQuantity, int orderCount) {
            this.price = price;
            this.totalQuantity = totalQuantity;
            this.orderCount = orderCount;
        }

        public static PriceLevelDto from(BigDecimal price, Deque<Order> orders) {
            BigDecimal total = orders.stream()
                    .map(Order::getRemainingQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PriceLevelDto(price, total, orders.size());
        }

        public BigDecimal getPrice()          { return price; }
        public BigDecimal getTotalQuantity()  { return totalQuantity; }
        public int getOrderCount()            { return orderCount; }
    }
}
