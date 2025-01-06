package dev.lydtech.dispatch.handler;

import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCreatedHandler {

    final DispatchService dispatchService;

    @KafkaListener(
        id = "orderConsumerClient",
        topics = "order.created",
        groupId = "dispatch.order.created.consumer"
    )
    public void listen(OrderCreated orderCreated) {
        log.info("Payload {}", orderCreated);
        try {
            dispatchService.process(orderCreated);
        } catch (Exception e) {
            log.error("Error processing payload {}", orderCreated, e);
        }
    }

}
