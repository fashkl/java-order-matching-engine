package matchingengine.matchingengine.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import matchingengine.matchingengine.domain.events.TradeExecutedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"trade-executed"})
@DirtiesContext
class TradeKafkaPublisherTest {

    @Autowired
    private TradeKafkaPublisher tradeKafkaPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp(
            @Autowired org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafka) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("trade-executed"));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void onTradeExecuted_publishesMessageToKafka() throws Exception {
        // Trigger group join and partition assignment before sending
        consumer.poll(Duration.ofMillis(500));

        var trade = buildTrade("BTC-USD");
        var event = TradeExecutedEvent.of(trade);

        // onTradeExecuted is @Async — returns immediately, sends on a background thread
        tradeKafkaPublisher.onTradeExecuted(event);

        ConsumerRecords<String, String> records = pollUntilRecordsArriveOrTimeout();
        assertFalse(records.isEmpty(), "Expected at least one Kafka record");

        var record = records.iterator().next();
        assertEquals("BTC-USD", record.key(), "Partition key must be the instrument");

        Map<?, ?> payload = objectMapper.readValue(record.value(), Map.class);
        assertEquals(event.eventId().toString(), payload.get("eventId"));
        assertEquals(trade.getId().toString(), payload.get("tradeId"));
        assertEquals("BTC-USD", payload.get("instrument"));
        assertEquals(trade.getBuyOrderId().toString(), payload.get("buyOrderId"));
        assertEquals(trade.getSellOrderId().toString(), payload.get("sellOrderId"));
        assertNotNull(payload.get("price"));
        assertNotNull(payload.get("quantity"));
        assertNotNull(payload.get("executedAt"));
        assertNotNull(payload.get("occurredAt"));
    }

    @Test
    void onTradeExecuted_nullEvent_doesNotThrow() {
        assertDoesNotThrow(() -> tradeKafkaPublisher.onTradeExecuted(null));
    }

    @Test
    void onTradeExecuted_instrumentIsThePartitionKey() throws Exception {
        // Trigger group join before sending
        consumer.poll(Duration.ofMillis(500));

        var trade = buildTrade("ETH-USD");
        var event = TradeExecutedEvent.of(trade);

        tradeKafkaPublisher.onTradeExecuted(event);

        ConsumerRecords<String, String> records = pollUntilRecordsArriveOrTimeout();
        assertFalse(records.isEmpty(), "Expected at least one Kafka record");
        records.forEach(r -> assertEquals("ETH-USD", r.key()));
    }

    // ---- helpers ----

    private ConsumerRecords<String, String> pollUntilRecordsArriveOrTimeout() {
        ConsumerRecords<String, String> records = ConsumerRecords.empty();
        long deadline = System.currentTimeMillis() + 10_000;
        while (records.isEmpty() && System.currentTimeMillis() < deadline) {
            records = consumer.poll(Duration.ofMillis(500));
        }
        return records;
    }

    private matchingengine.matchingengine.domain.Trade buildTrade(String instrument) {
        return new matchingengine.matchingengine.domain.Trade(
                instrument,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("45000.00"),
                new BigDecimal("1.5")
        );
    }
}
