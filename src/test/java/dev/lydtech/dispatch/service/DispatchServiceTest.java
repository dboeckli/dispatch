package dev.lydtech.dispatch.service;

import dev.lydtech.dispatch.message.OrderCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class DispatchServiceTest {

    private DispatchService service;

    @BeforeEach
    void setUp() {
        service = new DispatchService();
    }

    @Test
    void process() {
        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();
        service.process(orderCreated);
    }

}
