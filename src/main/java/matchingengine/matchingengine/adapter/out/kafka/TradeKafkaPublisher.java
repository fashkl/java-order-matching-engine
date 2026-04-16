package matchingengine.matchingengine.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import matchingengine.matchingengine.domain.Trade;
import matchingengine.matchingengine.domain.events.TradeExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TradeKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public TradeKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${kafka.topics.trade-executed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Async
    @EventListener(TradeExecutedEvent.class)
    public void onTradeExecuted(TradeExecutedEvent event) {
        if (event == null) {
            log.warn("Received null TradeExecutedEvent — skipping");
            return;
        }
        String message = convertToKafkaMessage(event);
        // Key = instrument so trades for the same book land on the same partition (preserves order)
        kafkaTemplate.send(topic, event.trade().getInstrument(), message);
        log.info("Published TradeExecutedEvent to Kafka — eventId={} instrument={} tradeId={}",
                event.eventId(), event.trade().getInstrument(), event.trade().getId());
    }

    private String convertToKafkaMessage(TradeExecutedEvent event) {
        try {
            Trade trade = event.trade();
            Map<String, Object> message = new HashMap<>();
            message.put("eventId", event.eventId().toString());
            message.put("occurredAt", event.occurredAt().toString());
            message.put("tradeId", trade.getId().toString());
            message.put("instrument", trade.getInstrument());
            message.put("buyOrderId", trade.getBuyOrderId().toString());
            message.put("sellOrderId", trade.getSellOrderId().toString());
            message.put("price", trade.getPrice());
            message.put("quantity", trade.getQuantity());
            message.put("executedAt", trade.getExecutedAt().toString());
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TradeExecutedEvent to JSON — eventId={}", event.eventId(), e);
            throw new RuntimeException("Failed to serialize TradeExecutedEvent", e);
        }
    }
}
