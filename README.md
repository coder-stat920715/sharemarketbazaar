# ShareMarketBazaar

Spring Boot 3 / Java 17 stock analysis and execution platform.

## Run (H2, no Kafka needed)
    mvn spring-boot:run

## Feed ticks (>= 27 ticks for EMA crossover; vary prices so a cross occurs)
    curl -X POST localhost:8080/api/v1/ticks -H 'Content-Type: application/json' \
      -d '{"symbol":"TCS","price":3500.25,"volume":1200}'
    curl localhost:8080/api/v1/analysis/TCS
    curl -X POST localhost:8080/api/v1/orders -H 'Content-Type: application/json' \
      -d '{"userId":"demo-user","symbol":"TCS","type":"BUY","quantity":10,"stopLoss":3450}'

## Production
    SPRING_PROFILES_ACTIVE=postgres KAFKA_BOOTSTRAP_SERVERS=... DB_HOST=... DB_USER=... DB_PASSWORD=... mvn spring-boot:run
Publish JSON StockTick messages to `market.ticks`, keyed by symbol.

## WebSocket (STOMP)
Connect `/ws`; subscribe `/topic/signals`, `/topic/signals/{symbol}`, `/topic/prices/{symbol}`;
send `/app/analysis/{symbol}` and read `/user/queue/analysis`.
