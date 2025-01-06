package dev.lydtech.dispatch.service;

import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.message.OrderDispatched;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static dev.lydtech.dispatch.service.DispatchService.ORDER_DISPATCHED_TOPIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DispatchServiceTest {

    private DispatchService service;

    @Mock
    private KafkaTemplate kafkaProducerMock;

    @BeforeEach
    void setUp() {
        kafkaProducerMock = mock(KafkaTemplate.class);
        service = new DispatchService(kafkaProducerMock);
    }

    @Test
    void testProcess_Success() throws Exception {
        when(kafkaProducerMock.send(anyString(), any(OrderDispatched.class))).thenReturn(mock(CompletableFuture.class));

        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();

        service.process(orderCreated);

        verify(kafkaProducerMock, times(1)).send(eq(ORDER_DISPATCHED_TOPIC), any(OrderDispatched.class));
    }

    @Test
    void testProcess_ProducerThrowsException() throws Exception {
        doThrow(new RuntimeException("Producer failure")).when(kafkaProducerMock).send(eq(ORDER_DISPATCHED_TOPIC), any(OrderDispatched.class));

        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();

        Exception exception = assertThrows(RuntimeException.class, () -> service.process(orderCreated));

        verify(kafkaProducerMock, times(1)).send(eq(ORDER_DISPATCHED_TOPIC), any(OrderDispatched.class));
        assertThat(exception.getMessage(), equalTo("Producer failure"));
    }

}
