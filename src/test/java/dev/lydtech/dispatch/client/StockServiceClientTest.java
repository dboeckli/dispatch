package dev.lydtech.dispatch.client;

import dev.lydtech.dispatch.error.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StockServiceClientTest {

    private RestTemplate restTemplateMock;
    
    private StockServiceClient stockServiceClient;

    private static final String STOCK_SERVICE_ENDPOINT = null;
    private static final String STOCK_SERVICE_QUERY = STOCK_SERVICE_ENDPOINT + "?item=my-item";

    @BeforeEach
    public void setUp() {
        restTemplateMock = mock(RestTemplate.class);
        stockServiceClient = new StockServiceClient(restTemplateMock);
    }

    @Test
    public void testCheckAvailability_Success() {
        ResponseEntity<String> response = new ResponseEntity<>("true", HttpStatusCode.valueOf(200));
        when(restTemplateMock.getForEntity(STOCK_SERVICE_QUERY, String.class)).thenReturn(response);
        
        assertThat(stockServiceClient.checkAvailability("my-item"), equalTo("true"));
        verify(restTemplateMock, times(1)).getForEntity(STOCK_SERVICE_QUERY, String.class);
    }

    @Test
    public void testCheckAvailability_ServerError() {
        doThrow(new HttpServerErrorException(HttpStatusCode.valueOf(500))).when(restTemplateMock).getForEntity(STOCK_SERVICE_QUERY, String.class);
        assertThrows(RetryableException.class, () -> stockServiceClient.checkAvailability("my-item"));
        verify(restTemplateMock, times(1)).getForEntity(STOCK_SERVICE_QUERY, String.class);
    }

    @Test
    public void testCheckAvailability_ResourceAccessException() {
        doThrow(new ResourceAccessException("access exception")).when(restTemplateMock).getForEntity(STOCK_SERVICE_QUERY, String.class);
        assertThrows(RetryableException.class, () -> stockServiceClient.checkAvailability("my-item"));
        verify(restTemplateMock, times(1)).getForEntity(STOCK_SERVICE_QUERY, String.class);
    }

    @Test
    public void testCheckAvailability_RuntimeException() {
        doThrow(new RuntimeException("general exception")).when(restTemplateMock).getForEntity(STOCK_SERVICE_QUERY, String.class);
        assertThrows(Exception.class, () -> stockServiceClient.checkAvailability("my-item"));
        verify(restTemplateMock, times(1)).getForEntity(STOCK_SERVICE_QUERY, String.class);
    }
}
