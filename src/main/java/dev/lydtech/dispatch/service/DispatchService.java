package dev.lydtech.dispatch.service;

import dev.lydtech.dispatch.client.StockServiceClient;
import dev.lydtech.message.DispatchCompleted;
import dev.lydtech.message.DispatchPreparing;
import dev.lydtech.message.OrderCreated;
import dev.lydtech.message.OrderDispatched;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static java.util.UUID.randomUUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchService {

    public static final String ORDER_DISPATCHED_TOPIC = "order.dispatched";
    public static final String DISPATCH_TRACKING_TOPIC = "dispatch.tracking";

    private static final UUID APPLICATION_ID = randomUUID();

    private final KafkaTemplate<String, Object> kafkaProducer;

    private final StockServiceClient stockServiceClient;

    public void process(String key, OrderCreated orderCreated) throws Exception {
        log.info("### Received OrderCreated Event: {} with key {}", orderCreated, key);
        String stockServiceAvailable = stockServiceClient.checkAvailability(orderCreated.getItem());

        if (Boolean.parseBoolean(stockServiceAvailable)) {
            this.sendDispatchPreparing(orderCreated, key);
            this.sendOrderDispatched(orderCreated, key);
            this.sendDispatchCompleted(orderCreated, key);
        } else {
            log.error("### Stock service is not available for item: {}", orderCreated.getItem());
        }
    }

    private void sendDispatchPreparing(OrderCreated orderCreated, String key) throws ExecutionException, InterruptedException {
        DispatchPreparing dispatchPreparing = DispatchPreparing.builder()
            .orderId(orderCreated.getOrderId())
            .build();
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, key, dispatchPreparing).get();
        log.info("\n### DispatchPreparing message for order {}\nhas been sent: {}\nwith key {}\nto {}",
            dispatchPreparing.getOrderId(), dispatchPreparing, key, DISPATCH_TRACKING_TOPIC);
    }

    private void sendOrderDispatched(OrderCreated orderCreated, String key) throws ExecutionException, InterruptedException {
        OrderDispatched orderDispatched = OrderDispatched.builder()
            .orderId(orderCreated.getOrderId())
            .processedById(APPLICATION_ID)
            .notes("Dispatched: " + orderCreated.getItem())
            .build();
        kafkaProducer.send(ORDER_DISPATCHED_TOPIC, key, orderDispatched).get();
        log.info("\n### OrderDispatched message for order {}\nhas been sent: {}\nwith key {}\nto {}",
            orderDispatched.getOrderId(), orderDispatched, key, ORDER_DISPATCHED_TOPIC);
    }

    private void sendDispatchCompleted(OrderCreated orderCreated, String key) throws ExecutionException, InterruptedException {
        DispatchCompleted dispatchCompleted = DispatchCompleted.builder()
            .orderId(orderCreated.getOrderId())
            .dispatchedDate(LocalDate.now().toString())
            .build();
        //ProducerRecord<String, Object> completedRecord = new ProducerRecord<>(DISPATCH_TRACKING_TOPIC, key, dispatchCompleted);
        //completedRecord.headers().add(new RecordHeader("__TypeId__", DispatchCompleted.class.getName().getBytes()));
        //kafkaProducer.send(completedRecord).get();
        kafkaProducer.send(DISPATCH_TRACKING_TOPIC, key, dispatchCompleted).get();
        log.info("\n### DispatchCompleted message for order {}\nhas been sent: {}\nwith key {}\nto {}"
            , dispatchCompleted.getOrderId(), dispatchCompleted, key, DISPATCH_TRACKING_TOPIC);
    }
}
