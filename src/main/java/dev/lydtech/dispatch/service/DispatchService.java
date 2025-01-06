package dev.lydtech.dispatch.service;

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

    static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";

    private final KafkaTemplate<String, Object> kafkaProducer;

    public void process(OrderCreated orderCreated) throws Exception {
        log.info("process payload {}", orderCreated);
        OrderDispatched orderDispatched = OrderDispatched.builder()
            .orderId(orderCreated.getOrderId())
            .build();
        kafkaProducer.send(ORDER_DISPATCHED_TOPIC, orderDispatched).get();
    }

}
