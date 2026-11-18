package dev.lydtech.dispatch.handler;

import dev.lydtech.message.DispatchCompleted;
import dev.lydtech.message.DispatchPreparing;
import dev.lydtech.message.OrderCreated;
import dev.lydtech.message.OrderDispatched;
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
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static dev.lydtech.dispatch.handler.OrderCreatedHandler.ORDER_CREATED_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.DISPATCH_TRACKING_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.ORDER_DISPATCHED_TOPIC;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("docker")
@DirtiesContext
@Slf4j
class OrderCreatedHandlerIT {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaConsumer<String, OrderCreated> consumerCreated;

    private KafkaConsumer<String, OrderDispatched> consumerDispatched;

    private KafkaConsumer<String, DispatchPreparing> consumerDispatchedPreparingTracking;

    private KafkaConsumer<String, DispatchCompleted> consumerDispatchedCompletedTracking;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        consumerCreated = new KafkaConsumer<>(createConsumerProps("consumerCreatedGroupId"));
        consumerDispatched = new KafkaConsumer<>(createConsumerProps("consumerDispatchedGroupId"));
        consumerDispatchedPreparingTracking = new KafkaConsumer<>(
                createConsumerProps("consumerDispatchedPreparingTrackingGroupId"));
        consumerDispatchedCompletedTracking = new KafkaConsumer<>(
                createConsumerProps("consumerDispatchedCompletedTrackingGroupId"));

        log.info("### Subscribing to {} topic", ORDER_CREATED_TOPIC);
        consumerCreated.subscribe(Collections.singletonList(ORDER_CREATED_TOPIC));
        log.info("### Subscribed to {}", consumerCreated.subscription());

        log.info("### Subscribing to {} topic", ORDER_DISPATCHED_TOPIC);
        consumerDispatched.subscribe(Collections.singletonList(ORDER_DISPATCHED_TOPIC));
        log.info("### Subscribed to {}", consumerDispatched.subscription());

        log.info("### Subscribing to Message DispatchPreparing {} on topic: ", DISPATCH_TRACKING_TOPIC);
        consumerDispatchedPreparingTracking.subscribe(Collections.singletonList(DISPATCH_TRACKING_TOPIC));
        log.info("### Subscribed to {}", consumerDispatchedPreparingTracking.subscription());

        log.info("### Subscribing to Message DispatchedCompleted {} on topic", DISPATCH_TRACKING_TOPIC);
        consumerDispatchedCompletedTracking.subscribe(Collections.singletonList(DISPATCH_TRACKING_TOPIC));
        log.info("### Subscribed to {}", consumerDispatchedCompletedTracking.subscription());
    }

    @AfterEach
    void tearDown() {
        if (consumerCreated != null) {
            consumerCreated.close();
        }
        if (consumerDispatched != null) {
            consumerDispatched.close();
        }
        if (consumerDispatchedPreparingTracking != null) {
            consumerDispatchedPreparingTracking.close();
        }
        if (consumerDispatchedCompletedTracking != null) {
            consumerDispatchedCompletedTracking.close();
        }
    }

    @Test
    void testOrderCreatedHandler() {
        String givenKey = randomUUID().toString();
        OrderCreated givenOrderCreated = OrderCreated.builder().orderId(UUID.randomUUID()).item("test-item").build();

        assertDoesNotThrow(() -> {
            kafkaTemplate.send(ORDER_CREATED_TOPIC, givenKey, givenOrderCreated).get();
        });
        log.info("Sent order: {}", givenOrderCreated);

        UUID orderIdCreated = getOrderCreatedMessageID(consumerCreated);
        UUID orderIdDispatched = getOrderDispatchedMessageID(consumerDispatched);
        UUID orderIdDispatchedPreparingTracking = getOrderDispatchedPreparingTrackingMessageID(
                consumerDispatchedPreparingTracking);
        UUID orderIdDispatchedCompletedTracking = getOrderDispatchedCompletedTrackingMessageID(
                consumerDispatchedCompletedTracking);

        assertAll("Order IDs should match across all messages", () -> assertNotNull(orderIdCreated),
                () -> assertNotNull(orderIdDispatched), () -> assertNotNull(orderIdDispatchedPreparingTracking),
                () -> assertNotNull(orderIdDispatchedCompletedTracking),
                () -> assertEquals(givenOrderCreated.getOrderId(), orderIdCreated),
                () -> assertEquals(givenOrderCreated.getOrderId(), orderIdDispatchedPreparingTracking),
                () -> assertEquals(givenOrderCreated.getOrderId(), orderIdDispatchedCompletedTracking),
                () -> assertEquals(givenOrderCreated.getOrderId(), orderIdDispatched));
    }

    private UUID getOrderCreatedMessageID(KafkaConsumer<String, OrderCreated> consumer) {
        // Wait for OrderDispatched message
        ConsumerRecords<String, OrderCreated> records = consumer.poll(Duration.ofSeconds(10));

        log.info("### OrderCreated message count: {}", records.count());
        assertFalse(records.isEmpty() || records.count() == 0, "Expected OrderCreated records, but none were received");

        ConsumerRecord<String, OrderCreated> createdRecord = null;
        for (ConsumerRecord<String, OrderCreated> r : records) {
            createdRecord = r;
        }
        assertNotNull(createdRecord, "orderCreated record is null");
        OrderCreated orderCreated = createdRecord.value();

        assertNotNull(orderCreated, "orderCreated message is null");
        return orderCreated.getOrderId();
    }

    private UUID getOrderDispatchedMessageID(KafkaConsumer<String, OrderDispatched> consumer) {
        // Wait for OrderDispatched message
        ConsumerRecords<String, OrderDispatched> records = consumer.poll(Duration.ofSeconds(10));

        log.info("### OrderDispatched message count: {}", records.count());
        assertFalse(records.isEmpty() || records.count() == 0,
                "Expected OrderDispatched records, but none were received");

        ConsumerRecord<String, OrderDispatched> dispatchedRecord = null;
        for (ConsumerRecord<String, OrderDispatched> r : records) {
            dispatchedRecord = r;
        }
        assertNotNull(dispatchedRecord, "OrderDispatched record is null");
        OrderDispatched orderDispatched = dispatchedRecord.value();

        assertNotNull(orderDispatched, "OrderDispatched message is null");
        return orderDispatched.getOrderId();
    }

    private UUID getOrderDispatchedPreparingTrackingMessageID(KafkaConsumer<String, DispatchPreparing> consumer) {
        // Wait for DispatchPreparing message
        ConsumerRecords<String, DispatchPreparing> records = consumer.poll(Duration.ofSeconds(10));

        log.info("### DispatchPreparing message count: {}", records.count());
        assertFalse(records.isEmpty() || records.count() == 0,
                "Expected DispatchPreparing records, but none were received");

        ConsumerRecord<String, DispatchPreparing> preparingRecord = null;
        for (ConsumerRecord<String, DispatchPreparing> r : records) {
            if (r.value() instanceof DispatchPreparing) {
                preparingRecord = r;
                break;
            }
        }
        assertNotNull(preparingRecord, "DispatchPreparing record is null");
        DispatchPreparing orderDispatchPreparing = preparingRecord.value();

        assertNotNull(orderDispatchPreparing, "DispatchPreparing message is null");
        return orderDispatchPreparing.getOrderId();
    }

    private UUID getOrderDispatchedCompletedTrackingMessageID(KafkaConsumer<String, DispatchCompleted> consumer) {
        // Wait for DispatchPreparing message
        ConsumerRecords<String, DispatchCompleted> records = consumer.poll(Duration.ofSeconds(10));

        log.info("### DispatchCompleted message count: {}", records.count());
        assertFalse(records.isEmpty() || records.count() == 0,
                "Expected DispatchCompleted records, but none were received");

        ConsumerRecord<String, DispatchCompleted> completedRecord = null;
        for (ConsumerRecord<String, DispatchCompleted> r : records) {
            if (r.value() instanceof DispatchCompleted) {
                completedRecord = r;
                break;
            }
        }
        assertNotNull(completedRecord, "DispatchCompleted record is null");
        DispatchCompleted orderDispatchCompleted = completedRecord.value();

        assertNotNull(orderDispatchCompleted, "orderDispatchCompleted message is null");
        return orderDispatchCompleted.getOrderId();
    }

    private Properties createConsumerProps(String groupId) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        consumerProps.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "dev.lydtech.message");
        return consumerProps;
    }

}
