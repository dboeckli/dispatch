package dev.lydtech.dispatch.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryableExceptionTest {

    @Test
    void keepsMessage() {
        RetryableException exception = new RetryableException("my message");

        assertThat(exception.getMessage()).isEqualTo("my message");
    }

    @Test
    void keepsCause() {
        Exception cause = new IllegalStateException("cause");

        RetryableException exception = new RetryableException(cause);

        assertThat(exception.getCause()).isSameAs(cause);
    }

}
