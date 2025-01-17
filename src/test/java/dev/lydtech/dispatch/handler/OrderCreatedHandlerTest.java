package dev.lydtech.dispatch.handler;

import dev.lydtech.dispatch.error.NotRetryableException;
import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.service.DispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void testListen_Success() throws Exception {
        String givenKey = randomUUID().toString();
        Integer givenPartition = 0;
        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(randomUUID())
            .item("item")
            .build();

        handler.listen(givenPartition, givenKey, orderCreated);
        verify(dispatchServiceMock, times(1)).process(givenKey, orderCreated);
    }

    @Test
    public void testListen_ServiceThrowsException() throws Exception {
        String givenKey = randomUUID().toString();
        Integer givenPartition = 0;
        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(randomUUID())
            .item("item")
            .build();

        doThrow(new RuntimeException("Service failure")).when(dispatchServiceMock).process(givenKey, orderCreated);

        NotRetryableException exception = assertThrows(NotRetryableException.class, () -> handler.listen(givenPartition, givenKey, orderCreated));
        assertEquals("java.lang.RuntimeException: Service failure", exception.getMessage());
        
        verify(dispatchServiceMock, times(1)).process(givenKey, orderCreated);
    }

}
