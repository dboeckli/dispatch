package dev.lydtech.dispatch.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockServiceHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;

    @Value("${dispatch.stockServiceEndpoint}")
    private String stockServiceEndpoint;

    private boolean wasDownLastCheck = true;

    @Override
    public Health health() {
        try {
            String response = restTemplate.getForObject(stockServiceEndpoint, String.class);

            if (response != null && response.equals("true")) {
                if (wasDownLastCheck) {
                    log.info("Stock Service is now reporting UP status at {}, response was {}", stockServiceEndpoint, response);
                    wasDownLastCheck = false;
                }
                return Health.up().build();
            } else {
                log.warn("Stock Service is not reporting UP status at {}, response was {}", stockServiceEndpoint, response);
                return Health.down().build();
            }
        } catch (Exception e) {
            log.warn("Stock Service is not reachable at {}", stockServiceEndpoint, e);
            return Health.down(e).build();
        }
    }
}
