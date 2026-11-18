package dev.lydtech.dispatch.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockServiceHealthIndicatorTest {

    private static final String STOCK_SERVICE_ENDPOINT = "http://localhost:9999/stock";

    private RestTemplate restTemplate;

    private StockServiceHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        healthIndicator = new StockServiceHealthIndicator(restTemplate);
        ReflectionTestUtils.setField(healthIndicator, "stockServiceEndpoint", STOCK_SERVICE_ENDPOINT);
    }

    @Test
    void reportsUpWhenStockServiceReturnsTrue() {
        when(restTemplate.getForObject(STOCK_SERVICE_ENDPOINT, String.class)).thenReturn("true");

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsUpOnRepeatedChecks() {
        when(restTemplate.getForObject(STOCK_SERVICE_ENDPOINT, String.class)).thenReturn("true");

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenStockServiceReturnsFalse() {
        when(restTemplate.getForObject(STOCK_SERVICE_ENDPOINT, String.class)).thenReturn("false");

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenStockServiceReturnsNoBody() {
        when(restTemplate.getForObject(STOCK_SERVICE_ENDPOINT, String.class)).thenReturn(null);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWithErrorWhenStockServiceIsUnreachable() {
        when(restTemplate.getForObject(STOCK_SERVICE_ENDPOINT, String.class))
            .thenThrow(new ResourceAccessException("connection refused"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

}
