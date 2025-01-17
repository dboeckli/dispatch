# Introduction to Kafka with Spring Boot - Standalone Wiremock

## Stock Service API

The API call is in the following format:

```
GET /api/stock?item=myItem
```

The wiremock is primed to return the following responses:

- `400` - if the item ends with the string `_400` e.g. `GET /api/stock?item=myItem_400`
- `500` - if the item ends with the string `_500`
- `502` - if the item ends with the string `_502` fail the first call then succeed. This allows for retry testing.
- `200` - for all other items

## Run the Wiremock

Wiremock is started with the docker profile using the compose.yaml file

The service will expose it rest services on port `8888`, which is where the Dispatch Service will attempt to call it.

## Test Wiremock Endpoints

To test the Wiremock endpoints, you can use the requests defined in the [wiremock.http](../../restRequest/wiremock.http) file.


