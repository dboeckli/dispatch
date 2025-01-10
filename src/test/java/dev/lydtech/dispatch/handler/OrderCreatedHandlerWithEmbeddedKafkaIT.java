package dev.lydtech.dispatch.handler;

import dev.lydtech.dispatch.message.DispatchPreparing;
import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.message.OrderDispatched;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.lydtech.dispatch.handler.OrderCreatedHandler.ORDER_CREATED_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.DISPATCH_TRACKING_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.ORDER_DISPATCHED_TOPIC;
import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test-embedded-kafka")
@DirtiesContext
@Slf4j
@EmbeddedKafka(controlledShutdown = true)
public class OrderCreatedHandlerWithEmbeddedKafkaIT {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private KafkaTestListener testListener;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public KafkaTestListener testListener() {
            return new KafkaTestListener();
        }

    }

    protected static class KafkaTestListener {
        AtomicInteger dispatchPreparingCounter = new AtomicInteger(0);
        AtomicInteger orderDispatchedCounter = new AtomicInteger(0);

        @KafkaListener(groupId = "KafkaIntegrationTest", topics = DISPATCH_TRACKING_TOPIC)
        void receiveDispatchPreparing(@Header(KafkaHeaders.RECEIVED_KEY) String key, 
                                      @Payload DispatchPreparing payload) {
            log.info("Received DispatchPreparing with key {} and payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            dispatchPreparingCounter.incrementAndGet();
        }

        @KafkaListener(groupId = "KafkaIntegrationTest", topics = ORDER_DISPATCHED_TOPIC)
        void receiveOrderDispatched(@Header(KafkaHeaders.RECEIVED_KEY) String key, 
                                    @Payload OrderDispatched payload) {
            log.info("Received DispatchPreparing with key {} and payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            orderDispatchedCounter.incrementAndGet();
        }
    }

    @BeforeEach
    public void setUp() {
        testListener.dispatchPreparingCounter.set(0);
        testListener.orderDispatchedCounter.set(0);

        // Wait until the partitions are assigned.
        registry.getListenerContainers().forEach(container ->
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic()));
    }
    
    @Test
    public void testOrderCreatedHandler() throws Exception {
        String givenKey = randomUUID().toString();
        OrderCreated givenOrderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("test-item")
            .build();
        
        sendMessage(ORDER_CREATED_TOPIC, givenKey, givenOrderCreated);

        await().atMost(3, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(testListener.dispatchPreparingCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(testListener.orderDispatchedCounter::get, equalTo(1));
    }

    private void sendMessage(String topic, String key, Object payload) throws Exception {
        kafkaTemplate.send(MessageBuilder
            .withPayload(payload)
                .setHeader(KafkaHeaders.KEY, key)
            .setHeader(KafkaHeaders.TOPIC, topic)
            .build()).get();
    }
    
}
