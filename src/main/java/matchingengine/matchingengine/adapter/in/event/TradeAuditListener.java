package matchingengine.matchingengine.adapter.in.event;

import matchingengine.matchingengine.domain.events.TradeExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TradeAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TradeAuditListener.class);

    @EventListener
    public void onTradeExecuted(TradeExecutedEvent event) {
        var trade = event.trade();
        log.info("TRADE EXECUTED — id={} instrument={} price={} qty={} buyOrder={} sellOrder={}",
                trade.getId(),
                trade.getInstrument(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getBuyOrderId(),
                trade.getSellOrderId());
    }
}
