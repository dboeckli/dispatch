package dev.lydtech.dispatch.handler;

import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCreatedHandler {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CREATED_TOPIC_GROUP_ID = "dispatch.order.created.group";

    final DispatchService dispatchService;

    @KafkaListener(
        id = "orderConsumerClient",
        topics = ORDER_CREATED_TOPIC,
        groupId = ORDER_CREATED_TOPIC_GROUP_ID)
    public void listen(@Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition, 
                       @Header(KafkaHeaders.RECEIVED_KEY) String key, 
                       @Payload OrderCreated orderCreated) {
        log.info("### Partition: {}, Key: {}, Payload: {}", partition, key, orderCreated);
        try {
            dispatchService.process(key, orderCreated);
        } catch (Exception e) {
            log.error("Error processing payload {}", orderCreated, e);
        }
    }
}
