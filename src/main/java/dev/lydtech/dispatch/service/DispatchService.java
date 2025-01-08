package dev.lydtech.dispatch.service;

import dev.lydtech.dispatch.message.DispatchPreparing;
import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.message.OrderDispatched;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchService {

    public static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    public static final String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";

    private final KafkaTemplate<String, Object> kafkaProducer;

    public void process(OrderCreated orderCreated) throws Exception {
        log.info("### process payload {}", orderCreated);

        DispatchPreparing dispatchPreparing = DispatchPreparing.builder()
            .orderId(orderCreated.getOrderId())
            .build();
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, dispatchPreparing).get();
        log.info("### dispatch tracking message for order {} has been sent", dispatchPreparing.getOrderId());
        
        OrderDispatched orderDispatched = OrderDispatched.builder()
            .orderId(orderCreated.getOrderId())
            .build();
        kafkaProducer.send(ORDER_DISPATCHED_TOPIC, orderDispatched).get();
        log.info("### dispatch message for order {} has been sent", orderDispatched.getOrderId());
    }

}
