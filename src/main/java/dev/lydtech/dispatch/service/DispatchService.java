package dev.lydtech.dispatch.service;

import dev.lydtech.dispatch.message.DispatchCompleted;
import dev.lydtech.dispatch.message.DispatchPreparing;
import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.message.OrderDispatched;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

import static java.util.UUID.randomUUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchService {

    public static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    public static final String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";
    
    private static final UUID APPLICATION_ID = randomUUID();

    private final KafkaTemplate<String, Object> kafkaProducer;

    public void process(String key, OrderCreated orderCreated) throws Exception {
        log.info("### Received OrderCreated Event: {} with key {}", orderCreated, key);

        DispatchPreparing dispatchPreparing = DispatchPreparing.builder()
            .orderId(orderCreated.getOrderId())
            .build();
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, key, dispatchPreparing).get();
        log.info("### dispatch tracking message for order {} has been sent: {} with key {}", dispatchPreparing.getOrderId(), dispatchPreparing, key);
        
        OrderDispatched orderDispatched = OrderDispatched.builder()
            .orderId(orderCreated.getOrderId())
            .processedById(APPLICATION_ID)
            .notes("Dispatched: " + orderCreated.getItem())
            .build();
        kafkaProducer.send(ORDER_DISPATCHED_TOPIC, key, orderDispatched).get();
        log.info("### order dispatch message for order {} has been sent: {} with key {}", orderDispatched.getOrderId(), orderDispatched, key);

        DispatchCompleted dispatchCompleted = DispatchCompleted.builder()
            .orderId(orderCreated.getOrderId())
            .dispatchedDate(LocalDate.now().toString())
            .build();
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, key, dispatchCompleted).get();
        log.info("### order tracking event complete message for order {} has been sent: {} with key {}", orderDispatched.getOrderId(), orderDispatched, key);
    }

}
