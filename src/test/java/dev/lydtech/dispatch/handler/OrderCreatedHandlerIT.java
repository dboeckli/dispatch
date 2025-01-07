package dev.lydtech.dispatch.handler;

import dev.lydtech.dispatch.message.OrderCreated;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static dev.lydtech.dispatch.handler.OrderCreatedHandler.ORDER_CREATED_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.DISPATCH_TRACKING_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.ORDER_DISPATCHED_TOPIC;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@Import(OrderCreatedHandler.class)
@ActiveProfiles("docker")
@Slf4j
public class OrderCreatedHandlerIT {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Test
    public void testOrderCreatedHandler() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(properties)) {
            adminClient.createTopics(Collections.singletonList(new NewTopic(ORDER_DISPATCHED_TOPIC, 1, (short) 1)));
            adminClient.createTopics(Collections.singletonList(new NewTopic(ORDER_CREATED_TOPIC, 1, (short) 1)));
            adminClient.createTopics(Collections.singletonList(new NewTopic(DISPATCH_TRACKING_TOPIC, 1, (short) 1)));
        }
        
        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("test-item")
            .build();

        assertDoesNotThrow(() -> {
            kafkaTemplate.send(ORDER_CREATED_TOPIC, orderCreated).get();
        });
        
        // TODO: is not traversing the listener OrderCreatedHandler
        log.info("Sent order: {}", orderCreated);
    }

    
}
