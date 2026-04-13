package matchingengine.matchingengine.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradeTest {

    @Test
    void trade_storesAllFieldsCorrectly() {
        UUID buyId = UUID.randomUUID();
        UUID sellId = UUID.randomUUID();

        Trade trade = new Trade("BTC-USD", buyId, sellId,
                new BigDecimal("45000"), new BigDecimal("1.5"));

        assertNotNull(trade.getId());
        assertEquals("BTC-USD", trade.getInstrument());
        assertEquals(buyId, trade.getBuyOrderId());
        assertEquals(sellId, trade.getSellOrderId());
        assertEquals(new BigDecimal("45000"), trade.getPrice());
        assertEquals(new BigDecimal("1.5"), trade.getQuantity());
        assertNotNull(trade.getExecutedAt());
    }

    @Test
    void trade_eachInstanceHasUniqueId() {
        UUID buyId = UUID.randomUUID();
        UUID sellId = UUID.randomUUID();

        Trade t1 = new Trade("BTC-USD", buyId, sellId, new BigDecimal("100"), new BigDecimal("1"));
        Trade t2 = new Trade("BTC-USD", buyId, sellId, new BigDecimal("100"), new BigDecimal("1"));

        assertNotEquals(t1.getId(), t2.getId());
    }
}
