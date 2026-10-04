# ShareMarketBazaar – Step-by-Step Testing Guide

This guide walks through testing the whole application with the supplied Postman collection
(`postman/ShareMarketBazaar.postman_collection.json`) and the WebSocket tester page
(`postman/websocket-test.html`).

---

## 1. Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| JDK | 17+ | run the app |
| Maven | 3.8+ | build/run |
| Postman (desktop or web agent) | recent | REST tests |
| Browser | any | WebSocket tester |
| Docker *(optional)* | any | Kafka test (section 8) |

## 2. Start the application

```bash
cd sharemarketbazaar
mvn spring-boot:run
```

Default profile is `h2` (in-memory DB, **Kafka listener disabled**), so no broker is needed.
Wait for the log line `Started Application` and `Seeded demo account 'demo-user' with balance 1,000,000.00`.

Quick check in a browser: <http://localhost:8080/actuator/health> → `{"status":"UP"}`.

> The tick store is **in memory**: restarting the app clears all market data. The H2 database is also wiped.

## 3. Import the collection

1. Postman → **Import** → select `postman/ShareMarketBazaar.postman_collection.json`.
2. Open the collection → **Variables** tab. Defaults are fine:

| Variable | Default | Meaning |
|----------|---------|---------|
| `baseUrl` | `http://localhost:8080` | change if you use another host/port |
| `userId` | `demo-user` | seeded trading account |
| `lastPrice` | *(empty)* | filled automatically by request 3.2 |

All other variables (`buySymbol`, `sellSymbol`, `orderId`, `limitBuy` …) are created automatically by scripts.
**Unique symbols are generated on every run**, so you can re-run the collection without restarting the app.

3. Make sure Postman's *Settings → General → SSL certificate verification* does not matter (we use plain HTTP).

## 4. Two ways to run

**A. Everything at once (recommended first time):** right-click the collection → **Run collection** →
keep the order (folders 0 → 5) → *Delay* 100 ms → **Run**. Expect **all tests green**
(≈ 42 requests, ≈ 150 assertions).

**B. Manually, one request at a time** – follow section 5. Read the *Description* of each request in Postman and the
**Test Results** tab after sending.

**C. CLI (CI-friendly):**
```bash
npm i -g newman
newman run postman/ShareMarketBazaar.postman_collection.json
```

> Why the "Load …" requests look odd: a pre-request script sends the first N ticks (via `pm.sendRequest`, strictly in
> order), and the request itself sends the **final** tick. This builds a 46-tick price history in one click. Open the
> Postman Console (*View → Show Postman Console*) to watch the individual tick calls.

---

## 5. Step-by-step manual test plan

### Folder 0 – Setup & Health
| Step | Request | Expected |
|------|---------|----------|
| 0.1 | `GET /actuator/health` | 200, `status: UP` |

### Folder 1 – Market data ingestion
| Step | Request | Expected |
|------|---------|----------|
| 1.1 | POST tick `{"symbol":"tcs","price":3500.25,"volume":1200}` | **202**, response `symbol` = `TCS`, `timestamp` auto-filled |
| 1.2 | POST tick with `price: -5` | **400**, detail mentions `price` |
| 1.3 | POST tick without `symbol` | **400**, detail mentions `symbol` |
| 1.4 | POST truncated JSON | **400** |

What it proves: validation, normalisation, and that the REST path equals the Kafka path (`MarketDataIngestionService.ingest`).

### Folder 2 – Indicator maths (hand-verifiable)
| Step | Request | Expected |
|------|---------|----------|
| 2.1 | Load 2 ticks: (100, vol 100), (110, vol 300) | 202 |
| 2.2 | `GET /api/v1/analysis/{vwapSymbol}` | `vwap` = **107.5** (= (100·100 + 110·300) / 400); no EMA/RSI; signal `HOLD` – *Warming up* |
| 2.3 | Load 16 rising ticks 100 → 115 | 202 |
| 2.4 | `GET /analysis/{rsiSymbol}` | `rsi14` = **100**, RSI bias `BEARISH`; EMA absent (needs 27 ticks) |
| 2.5 | POST a RELIANCE tick | 202 |
| 2.6 | `GET /analysis/RELIANCE` | **no** `vwap` – the Factory builds only `[EMA_CROSSOVER, RSI]` for RELIANCE (see `ticker-indicators` in `application.yml`) |

Minimum data per indicator: VWAP 1 tick · RSI 15 ticks · EMA crossover 27 ticks.

### Folder 3 – Signal generation (EMA crossover + RSI guard)
Scenario data (all volume 1000):

| Scenario | Price path | Last tick | Result |
|----------|-----------|-----------|--------|
| BUY | 1000 ↓ to 985 (30 ticks, −0.5) then ↑ +0.5/tick | 992.5 (tick 46) | EMA12 crosses **above** EMA26 (≈ 990.1989 vs 990.1905), RSI ≈ 67.1 (< 70) → **BUY** |
| SELL | 1000 ↑ to 1015 then ↓ −0.5/tick | 1007.5 (tick 46) | EMA12 crosses **below** EMA26, RSI ≈ 32.9 (> 30) → **SELL** |
| Guard | 1000 ↓ to 985 then ↑ +1.5/tick | 998.5 (tick 40) | Golden cross but RSI ≈ 73.99 (≥ 70) → blocked → **HOLD** |

| Step | Request | Expected |
|------|---------|----------|
| 3.1 | Load BUY scenario | 202 |
| 3.2 | `GET /analysis/{buySymbol}` | `signal.signal=BUY`, `stopLoss` ≈ **977.6125** (−1.5 %), `targetPrice` ≈ **1022.275** (+3 %), confidence 0.55–1.0. Saves `lastPrice` (992.5) |
| 3.3 | `GET /analysis/{buySymbol}/signals` | exactly **1** persisted BUY |
| 3.4 / 3.5 | Load + verify SELL | `SELL`, stopLoss ≈ 1022.6125 (+1.5 %), target ≈ 977.275 (−3 %) |
| 3.6 / 3.7 | Load + verify guard | `crossover = 1` yet `signal = HOLD`, reason *"Bullish EMA cross blocked by RSI guard"* |
| 3.8 | `GET /analysis/{guardSymbol}/signals` | `[]` – HOLD is never persisted |

### Folder 4 – Orders, Risk Engine, OMS
Run **after folder 3** (needs `buySymbol` and `lastPrice`). Demo account `demo-user` starts with 1,000,000.

| Step | Scenario | Expected |
|------|----------|----------|
| 4.1 | Market BUY 10, stop-loss 1.5 % below | **201**, `EXECUTED`, `executionPrice` = 992.5. Saves `orderId` |
| 4.2 | GET order | 200 |
| 4.3 | List orders for `demo-user` | newest first, contains 4.1 |
| 4.4 | BUY without stop-loss | **422**, `REJECTED`, *Stop-loss is mandatory* |
| 4.5 | Stop-loss 20 % away | 422, *outside allowed range* (0.1 %–10 %) |
| 4.6 | Stop-loss above price | 422, *must be below the order price* |
| 4.7 | Qty 1000 (≈ 992,500) | 422, *exceeds max order value* (500,000) |
| 4.8 | Qty 5001 | 422, *Position limit exceeded* (max 5000) – other violations listed too |
| 4.9 | SELL 500 while holding 10 | 422, *Insufficient position* |
| 4.10 | Unknown user `ghost` | 422, *Unknown account* |
| 4.11 | `quantity: 0`, no `type` | **400** with `errors.quantity`, `errors.type` |
| 4.12 | Market SELL 4 | 201 `EXECUTED` (position 10 → 6) |
| 4.13 | Limit BUY at lastPrice−5 | 201 **`PENDING`** (market is above the limit) |
| 4.14 | POST tick at lastPrice−6 | 202 – OMS observer fills the resting order |
| 4.15 | GET the pending order | now **`EXECUTED`** at the tick price |
| 4.16 | Limit BUY at 90 % of market | 201 `PENDING` |
| 4.17 | `DELETE /orders/{id}?userId=demo-user` | 200 `CANCELLED` |
| 4.18 | Cancel again | **409** *Only PENDING orders can be cancelled* |
| 4.19 | Cancel an EXECUTED order | 409 |
| 4.20 | Cancel with another `userId` | **404** |
| 4.21 | Order on a symbol with no ticks | **404** *No market data* |
| 4.22 | GET order `99999999` | 404 |

HTTP status cheat-sheet: `201` accepted (executed or resting) · `422` risk-rejected (order persisted as `REJECTED`) ·
`400` validation · `404` not found · `409` illegal state / concurrent update.

### Folder 5 – Error handling
| 5.1 | `GET /analysis/NOSUCHSYM` | 404 RFC-7807 body |

---

## 6. Verifying balances & positions (H2 console)

There is no REST endpoint for balances, so inspect the DB:

1. Open <http://localhost:8080/h2-console>.
2. JDBC URL: `jdbc:h2:mem:sharemarket` · user `sa` · empty password → **Connect**.
3. Run:
```sql
SELECT * FROM accounts;                                   -- balance reduced by executed BUYs, increased by SELLs
SELECT * FROM positions WHERE symbol LIKE 'BUY%';         -- quantity 6 + 10 = 16 after folder 4, average_cost weighted
SELECT id, symbol, order_type, status, quantity, execution_price, rejection_reason FROM orders ORDER BY id;
SELECT * FROM signals ORDER BY id;
```
Expected after one full run: position quantity **16** (10 − 4 + 10); BUY executions at 992.5 and 986.5 → average cost recalculated
on buys only (SELLs do not change it).

## 7. Testing the WebSocket / STOMP feed

Postman cannot speak STOMP framing conveniently, so use the supplied page:

1. Open `postman/websocket-test.html` in a browser (double-click is fine).
2. Server `http://localhost:8080/ws`, symbol e.g. `LIVE1` → **Connect** (status turns *connected*).
3. Click **All signals**, **Live prices for symbol**, **My analysis replies**, **My errors**.
4. In Postman send ticks for `LIVE1`. Easiest: copy a BUY scenario – run request 3.1 and then, in the page, change the
   symbol field to the value of `{{buySymbol}}` *before* running (subscribe first, then run 3.1). Alternatively use curl:
   ```bash
   for p in $(python3 -c "print(' '.join(str(round(1000-0.5*i,2)) for i in range(31)))"); do
     curl -s -X POST localhost:8080/api/v1/ticks -H 'Content-Type: application/json' \
       -d "{\"symbol\":\"LIVE1\",\"price\":$p,\"volume\":1000}" >/dev/null; done
   for p in $(python3 -c "print(' '.join(str(round(985+0.5*i,2)) for i in range(1,16)))"); do
     curl -s -X POST localhost:8080/api/v1/ticks -H 'Content-Type: application/json' \
       -d "{\"symbol\":\"LIVE1\",\"price\":$p,\"volume\":1000}" >/dev/null; done
   ```
5. Expected in the page: a `/topic/prices/LIVE1` message per tick, and a `/topic/signals` + `/topic/signals/LIVE1` **BUY** message
   at the crossover tick.
6. Click **Latest signal snapshot** → immediate reply with the most recent signal (`@SubscribeMapping`).
7. Click **Request full analysis** → reply on `/user/queue/analysis`. For an unknown symbol the error arrives on `/user/queue/errors`.

Raw WebSocket (no SockJS) is also available at `ws://localhost:8080/ws/websocket` for STOMP clients.

To also stream HOLD signals set `sharemarket.signal.emit-hold: true` and restart.

## 8. Optional: testing the real Kafka path

```bash
docker run -d --name kafka -p 9092:9092 apache/kafka:3.8.0
KAFKA_ENABLED=true mvn spring-boot:run          # h2 profile + Kafka consumer on
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic market.ticks \
  --property parse.key=true --property key.separator=:
```
Then type lines (key `:` JSON value):
```
INFY:{"symbol":"INFY","price":1500.5,"volume":200}
INFY:{"symbol":"INFY","price":1501.0,"volume":300}
```
Check `GET /api/v1/analysis/INFY` → `tickCount: 2`, `vwap` ≈ 1500.8. A malformed line (e.g. `INFY:not-json`) is logged and skipped
without stopping the consumer (ErrorHandlingDeserializer).

## 9. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Connection refused | App not started or different port – update `baseUrl` |
| 3.2 fails: ticks count lower than 46 | Pre-request ticks failed; open Postman Console, check the app is reachable |
| Folder 4 error *Run folder 3 first* | `lastPrice` is empty – run 3.1 and 3.2 first |
| 4.x unexpected `Insufficient balance` | The demo account was drained by many runs – restart the app (resets H2) |
| 404 *No market data* after restart | Tick store is in memory – re-run folder 3 |
| Tests in folder 2 fail on re-run of only one half | Run the *Load* request and its *Verify* partner together (the Load request creates a fresh symbol) |
| WebSocket page cannot connect | Check the server field; CDN scripts need internet access |
