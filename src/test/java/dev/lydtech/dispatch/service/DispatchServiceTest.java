package dev.lydtech.dispatch.service;

import dev.lydtech.dispatch.message.DispatchCompleted;
import dev.lydtech.dispatch.message.DispatchPreparing;
import dev.lydtech.dispatch.message.OrderCreated;
import dev.lydtech.dispatch.message.OrderDispatched;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static dev.lydtech.dispatch.service.DispatchService.DISPATCH_TRACKING_TOPIC;
import static dev.lydtech.dispatch.service.DispatchService.ORDER_DISPATCHED_TOPIC;
import static java.util.UUID.randomUUID;
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
        String givenKey = randomUUID().toString();
        
        when(kafkaProducerMock.send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class))).thenReturn(mock(CompletableFuture.class));
        when(kafkaProducerMock.send(eq(ORDER_DISPATCHED_TOPIC), eq(givenKey), any(OrderDispatched.class))).thenReturn(mock(CompletableFuture.class));
        when(kafkaProducerMock.send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchCompleted.class))).thenReturn(mock(CompletableFuture.class));

        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();

        service.process(givenKey, orderCreated);

        verify(kafkaProducerMock, times(1)).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class));
        verify(kafkaProducerMock, times(1)).send(eq(ORDER_DISPATCHED_TOPIC), eq(givenKey), any(OrderDispatched.class));
        verify(kafkaProducerMock, times(1)).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchCompleted.class));
    }

    @Test
    void testProcess_OrderDispatchedProducerThrowsException() {
        String givenKey = randomUUID().toString();
        
        when(kafkaProducerMock.send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class))).thenReturn(mock(CompletableFuture.class));
        String errorMessage = "order dispatched producer failure";
        doThrow(new RuntimeException(errorMessage)).when(kafkaProducerMock).send(eq(ORDER_DISPATCHED_TOPIC), eq(givenKey), any(OrderDispatched.class));

        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();
        Exception exception = assertThrows(RuntimeException.class, () -> service.process(givenKey, orderCreated));

        verify(kafkaProducerMock, times(1)).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class));
        verify(kafkaProducerMock, times(1)).send(eq(ORDER_DISPATCHED_TOPIC), eq(givenKey), any(OrderDispatched.class));
        verifyNoMoreInteractions(kafkaProducerMock);
        assertThat(exception.getMessage(), equalTo(errorMessage));
    }

    @Test
    public void testProcess_DispatchTrackingProducerThrowsException() {
        String givenKey = randomUUID().toString();
        String errorMessage = "dispatch tracking producer failure";
        doThrow(new RuntimeException(errorMessage)).when(kafkaProducerMock).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class));

        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();
        Exception exception = assertThrows(RuntimeException.class, () -> service.process(givenKey, orderCreated));

        verify(kafkaProducerMock, times(1)).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class));
        verifyNoMoreInteractions(kafkaProducerMock);
        assertThat(exception.getMessage(), equalTo(errorMessage));
    }

    @Test
    public void testProcess_DispatchCompletedProducerThrowsException() {
        String givenKey = randomUUID().toString();
        String errorMessage = "dispatch tracking completed producer failure";
        when(kafkaProducerMock.send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class))).thenReturn(mock(CompletableFuture.class));
        when(kafkaProducerMock.send(eq(ORDER_DISPATCHED_TOPIC), eq(givenKey), any(OrderDispatched.class))).thenReturn(mock(CompletableFuture.class));
        doThrow(new RuntimeException(errorMessage)).when(kafkaProducerMock).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchCompleted.class));

        OrderCreated orderCreated = OrderCreated.builder()
            .orderId(UUID.randomUUID())
            .item("item")
            .build();
        Exception exception = assertThrows(RuntimeException.class, () -> service.process(givenKey, orderCreated));

        verify(kafkaProducerMock, times(1)).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchPreparing.class));
        verify(kafkaProducerMock, times(1)).send(eq(ORDER_DISPATCHED_TOPIC), eq(givenKey), any(OrderDispatched.class));
        verify(kafkaProducerMock, times(1)).send(eq(DISPATCH_TRACKING_TOPIC), eq(givenKey), any(DispatchCompleted.class));
        verifyNoMoreInteractions(kafkaProducerMock);
        assertThat(exception.getMessage(), equalTo(errorMessage));
    }

}
