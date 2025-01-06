package dev.lydtech.dispatch.handler;

import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.service.DispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class OrderCreatedHandlerTest {

    private OrderCreatedHandler handler;
    private DispatchService dispatchServiceMock;

    @BeforeEach
    void setUp() {
        dispatchServiceMock = mock(DispatchService.class);
        handler = new OrderCreatedHandler(dispatchServiceMock);
    }

    @Test
    void listen() {
        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();

        handler.listen(orderCreated);
        verify(dispatchServiceMock, times(1)).process(orderCreated);
    }

}
