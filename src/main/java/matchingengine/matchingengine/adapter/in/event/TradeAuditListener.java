package matchingengine.matchingengine.adapter.in.event;

import matchingengine.matchingengine.domain.events.TradeExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TradeAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TradeAuditListener.class);

    /**
     * Fires on a separate thread after the transaction commits.
     * No transaction present (Phase 1-5) → fires immediately after publish.
     * Transaction present (Phase 6+) → fires only on successful commit,
     * so a DB rollback never produces a phantom audit log entry.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTradeExecuted(TradeExecutedEvent event) {
        var trade = event.trade();
        log.info("TRADE EXECUTED — eventId={} instrument={} price={} qty={} buyOrder={} sellOrder={} occurredAt={}",
                event.eventId(),
                trade.getInstrument(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getBuyOrderId(),
                trade.getSellOrderId(),
                event.occurredAt());
    }
}
