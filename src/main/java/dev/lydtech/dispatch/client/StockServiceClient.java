package dev.lydtech.dispatch.client;

import dev.lydtech.dispatch.error.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockServiceClient {

    private final RestTemplate restTemplate;

    @Value("${dispatch.stockServiceEndpoint}")
    private String stockServiceEndpoint;

    public String checkAvailability(String item) {
        String url = stockServiceEndpoint + "?item=" + item;
        log.info("Attempting to call stock service at URL: {}", url);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.info("Call to stock service was successull. response was: {}", response);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("error " + response.getStatusCode());
            }
            return response.getBody();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.warn("Failure calling external service", e);
            throw new RetryableException(e);
        } catch (Exception e) {
            log.error("Exception thrown: " + e.getClass().getName(), e);
            throw e;
        }
    }
    
}
