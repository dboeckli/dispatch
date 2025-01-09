package dev.lydtech.dispatch.handler;

import dev.lydtech.dispatch.message.DispatchPreparing;
import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.message.OrderDispatched;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static dev.lydtech.dispatch.handler.OrderCreatedHandler.ORDER_CREATED_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.DISPATCH_TRACKING_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.ORDER_DISPATCHED_TOPIC;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("docker")
@DirtiesContext
@Slf4j
public class OrderCreatedHandlerIT {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaConsumer<String, OrderCreated> consumerCreated;
    private KafkaConsumer<String, OrderDispatched> consumerDispatched;
    private KafkaConsumer<String, DispatchPreparing> consumerDispatchedTracking;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        consumerCreated = new KafkaConsumer<>(createConsumerProps("consumerCreatedGroupId"));
        consumerDispatched = new KafkaConsumer<>(createConsumerProps("consumerDispatchedGroupId"));
        consumerDispatchedTracking = new KafkaConsumer<>(createConsumerProps("consumerDispatchedTrackingGroupId"));

        log.info("### Subscribing to {} topic", ORDER_CREATED_TOPIC);
        consumerCreated.subscribe(Collections.singletonList(ORDER_CREATED_TOPIC));
        log.info("### Subscribed to {}", consumerCreated.subscription());

        log.info("### Subscribing to {} topic", ORDER_DISPATCHED_TOPIC);
        consumerDispatched.subscribe(Collections.singletonList(ORDER_DISPATCHED_TOPIC));
        log.info("### Subscribed to {}", consumerDispatched.subscription());

        log.info("### Subscribing to {} topic", DISPATCH_TRACKING_TOPIC);
        consumerDispatchedTracking.subscribe(Collections.singletonList(DISPATCH_TRACKING_TOPIC));
        log.info("### Subscribed to {}", consumerDispatchedTracking.subscription());
    }

    @AfterEach
    void tearDown() {
        if (consumerCreated != null) {
            consumerCreated.close();
        }
        if (consumerDispatched != null) {
            consumerDispatched.close();
        }
        if (consumerDispatchedTracking != null) {
            consumerDispatchedTracking.close();
        }
    }
    
    @Test
    public void testOrderCreatedHandler() {
        OrderCreated givenOrderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("test-item")
            .build();

        assertDoesNotThrow(() -> {
            kafkaTemplate.send(ORDER_CREATED_TOPIC, givenOrderCreated).get();
        });
        log.info("Sent order: {}", givenOrderCreated);

        UUID orderIdCreated = getOrderCreatedMessageID(consumerCreated);
        UUID orderIdDispatchedTracking = getOrderDispatchedTrackingMessageID(consumerDispatchedTracking);
        UUID orderIdDispatched = getOrderDispatchedMessageID(consumerDispatched);

        assertAll("Order IDs should match across all messages",
            () -> assertNotNull(orderIdCreated),
            () -> assertNotNull(orderIdDispatchedTracking),
            () -> assertNotNull(orderIdDispatched),
            () -> assertEquals(givenOrderCreated.getOrderId(), orderIdCreated),
            () -> assertEquals(givenOrderCreated.getOrderId(), orderIdDispatchedTracking),
            () -> assertEquals(givenOrderCreated.getOrderId(), orderIdDispatched)
        );
    }

    private UUID getOrderCreatedMessageID(KafkaConsumer<String, OrderCreated> consumer) {
        // Wait for OrderDispatched message
        ConsumerRecords<String, OrderCreated> records = consumer.poll(Duration.ofSeconds(10));

        log.info("### OrderCreated message count: {}", records.count());
        if (records.isEmpty() || records.count() == 0) {
            return null;
        }

        ConsumerRecord<String, OrderCreated> record = null;
        for (ConsumerRecord<String, OrderCreated> r : records) {
            record = r;
        }
        if (record == null) {
            return null;
        }
        OrderCreated orderCreated = record.value();
        return orderCreated.getOrderId();
    }
    
    private UUID getOrderDispatchedMessageID(KafkaConsumer<String, OrderDispatched> consumer) {
        // Wait for OrderDispatched message
        ConsumerRecords<String, OrderDispatched> records = consumer.poll(Duration.ofSeconds(10));

        log.info("### OrderDispatched message count: {}", records.count());
        if (records.isEmpty() || records.count() == 0) {
            return null;
        }

        ConsumerRecord<String, OrderDispatched> record = null;
        for (ConsumerRecord<String, OrderDispatched> r : records) {
            record = r;
        }
        if (record == null) {
            return null;
        }
        OrderDispatched orderDispatched = record.value();

        assertNotNull(orderDispatched, "OrderDispatched message is null");
        return orderDispatched.getOrderId();
    }

    private UUID getOrderDispatchedTrackingMessageID(KafkaConsumer<String, DispatchPreparing> consumer) {
        // Wait for DispatchPreparing message
        ConsumerRecords<String, DispatchPreparing> records = consumer.poll(Duration.ofSeconds(10));

        log.info("### DispatchPreparing message count: {}", records.count());
        if (records.isEmpty() || records.count() == 0) {
            return null;
        }

        ConsumerRecord<String, DispatchPreparing> record = null;
        for (ConsumerRecord<String, DispatchPreparing> r : records) {
            record = r;
        }
        if (record == null) {
            return null;
        }
        DispatchPreparing orderDispatchPreparing = record.value();

        assertNotNull(orderDispatchPreparing, "DispatchPreparing message is null");
        return orderDispatchPreparing.getOrderId();
    }
    
    private Properties createConsumerProps(String groupId) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return consumerProps;
    }
}
