package dev.lydtech.dispatch.handler;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.lydtech.dispatch.message.DispatchCompleted;
import dev.lydtech.dispatch.message.DispatchPreparing;
import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.message.OrderDispatched;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaHandler;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.wiremock.spring.EnableWireMock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.lydtech.dispatch.handler.OrderCreatedHandler.ORDER_CREATED_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.DISPATCH_TRACKING_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.ORDER_DISPATCHED_TOPIC;
import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test-embedded-kafka")
@DirtiesContext
@Slf4j
@EmbeddedKafka(controlledShutdown = true)
@EnableWireMock
public class OrderCreatedHandlerWithEmbeddedKafkaIT {

    @Value("${wiremock.server.baseUrl}")
    private String wireMockUrl;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    RestTemplate restTemplate;

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

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("dispatch.stockServiceEndpoint", () -> "${wiremock.server.baseUrl}/api/stock");
    }

    @KafkaListener(groupId = "KafkaIntegrationTest", topics = {DISPATCH_TRACKING_TOPIC, ORDER_DISPATCHED_TOPIC})
    protected static class KafkaTestListener {
        AtomicInteger dispatchPreparingCounter = new AtomicInteger(0);
        AtomicInteger orderDispatchedCounter = new AtomicInteger(0);
        AtomicInteger dispatchCompletedCounter = new AtomicInteger(0);

        @KafkaHandler
        void receiveDispatchPreparing(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                                      @Payload DispatchPreparing payload) {
            log.info("Received DispatchPreparing with key {} and payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            dispatchPreparingCounter.incrementAndGet();
        }

        @KafkaHandler
        void receiveOrderDispatched(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                                    @Payload OrderDispatched payload) {
            log.info("Received DispatchPreparing with key {} and payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            orderDispatchedCounter.incrementAndGet();
        }

        @KafkaHandler
        void receiveDispatchCompleted(@Header(KafkaHeaders.RECEIVED_KEY) String key,
                                      @Payload DispatchCompleted payload) {
            log.info("Received DispatchCompleted with key {} and payload: {}", key, payload);
            assertNotNull(key);
            assertNotNull(payload);
            dispatchCompletedCounter.incrementAndGet();
        }
    }

    @BeforeEach
    public void setUp() {
        log.info("WireMock URL: {}", wireMockUrl);
        
        testListener.dispatchPreparingCounter.set(0);
        testListener.orderDispatchedCounter.set(0);
        testListener.dispatchCompletedCounter.set(0);

        // Wait until the partitions are assigned.
        registry.getListenerContainers().forEach(container -> {
            String[] topics = container.getContainerProperties().getTopics();
            //Map<String, Collection<TopicPartition>> assignments =  container.getAssignmentsByClientId();
            int expectedPartitions = 0;
            for (String topic : topics) {
                expectedPartitions = (topic.equals(ORDER_CREATED_TOPIC) ? 2 : 4);
                log.info("Waiting for assignment of topic: {}. expected partitions {}", topic, expectedPartitions);
            }
            ContainerTestUtils.waitForAssignment(container, expectedPartitions);
        });
    }
    

    @Test
    public void testOrderCreatedHandler() throws Exception {
        stubFor(get(urlPathMatching("/api/stock.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("true")
            ));
        
        UUID givenKey = randomUUID();
        OrderCreated givenOrderCreated = OrderCreated.builder()
            .orderId(randomUUID())
            .item("test-item")
            .build();

        sendMessage(ORDER_CREATED_TOPIC, givenKey.toString(), givenOrderCreated);

        await().atMost(3, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(testListener.dispatchPreparingCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(testListener.orderDispatchedCounter::get, equalTo(1));
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(testListener.dispatchCompletedCounter::get, equalTo(1));
    }

    @Test
    public void testOrderDispatchFlow_NotRetryableException() throws Exception {
        stubFor(get(urlPathMatching("/api/stock.*"))
            .willReturn(aResponse()
                .withStatus(400)
            ));

        String givenKey = randomUUID().toString();
        OrderCreated givenOrderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("test-item")
            .build();

        sendMessage(ORDER_CREATED_TOPIC, givenKey, givenOrderCreated);

        TimeUnit.SECONDS.sleep(3);
        
        assertThat(testListener.dispatchPreparingCounter.get(), equalTo(0));
        assertThat(testListener.orderDispatchedCounter.get(), equalTo(0));
        assertThat(testListener.dispatchCompletedCounter.get(), equalTo(0));
    }

    /**
     * The call to the stock service is stubbed to initially return a 503 Service Unavailable response, resulting in a
     * retryable exception being thrown.  On the subsequent attempt it is stubbed to then succeed, so the outbound events
     * are sent.
     */
    @Test
    public void testOrderDispatchFlow_RetryThenSuccess() throws Exception {
        // First stub: Respond with 503 on the first call
        String TestScenario = "Retry Scenario";
        stubFor(get(urlPathMatching("/api/stock.*"))
            .inScenario(TestScenario)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service unavailable"))
            .willSetStateTo("Failed Once"));
        // Second stub: Respond with 200 on subsequent calls
        stubFor(get(urlPathMatching("/api/stock.*"))
            .inScenario(TestScenario)
            .whenScenarioStateIs("Failed Once")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("true")));

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
        await().atMost(1, TimeUnit.SECONDS).pollDelay(100, TimeUnit.MILLISECONDS)
            .until(testListener.dispatchCompletedCounter::get, equalTo(1));
    }

    private void sendMessage(String topic, String key, Object payload) throws Exception {
        kafkaTemplate.send(MessageBuilder
            .withPayload(payload)
            .setHeader(KafkaHeaders.KEY, key)
            .setHeader(KafkaHeaders.TOPIC, topic)
            .build()).get();
    }

}
