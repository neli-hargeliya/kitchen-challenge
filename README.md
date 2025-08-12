# Kitchen Challenge — README

## Overview

Reactive Spring Boot app that simulates a kitchen’s order flow and reports results to the Challenge API.

- **Stack:** Spring Boot 3 (WebFlux), R2DBC (PostgreSQL), MapStruct, Lombok, Reactor.
- **Core logic:** in-memory storages + decay/expiry; persistent **ledger** of actions (PLACE/MOVE/PICKUP/DISCARD).
- **Simulator:** pulls orders from `/new`, runs the scenario, and posts results to `/solve`.

> **Time units**  
> • `freshness` in incoming orders — **seconds**.  
> • Timestamps sent to Challenge `/solve` — **microseconds**.  
> • Ledger timestamps in DB/API — ISO `Instant` (date-time).

---

## Architecture (short)

- **StorageService** — in-memory storages with capacities (HEATER/COOLER: 6, SHELF: 12), min-heap for shelf discard, microsecond-precision decay (2× on non-ideal shelf).
- **KitchenService** — orchestration + persistence:
    1) try ideal → else SHELF;
    2) if SHELF full: move one from SHELF to ideal;
    3) else: discard soonest to expire;
    4) log actions to ledger (DB).
- **KitchenSimulator** — fetch `/new`, place at rate, wait random `[min;max]`, pickup, collect actions in run window, POST `/solve`.
- **Persistence** — R2DBC Postgres: `orders` (snapshot), `actions` (immutable ledger).

---

## API

OpenAPI spec lives in the repo (see `openapi.yaml`). With springdoc you also get Swagger UI:

- UI: `http://localhost:8080/swagger-ui.html`
- JSON: `http://localhost:8080/v3/api-docs`

Key endpoints:

- `POST /api/orders` — place an order.
- `POST /api/orders/{id}/pickup` — pickup (or discard if expired at removal).
- `GET /api/ledger` — list actions.
- `POST /api/simulation/run` — **fire-and-forget** start of simulation (returns immediately).

### Curl samples

```bash
# Place order
curl -X POST http://localhost:8080/api/orders   -H 'Content-Type: application/json'   -d '{"id":"abc123","name":"Cheese Pizza","temp":"HOT","freshness":120}'

# Pickup
curl -X POST http://localhost:8080/api/orders/abc123/pickup

# Ledger
curl -X GET http://localhost:8080/api/ledger

# Start simulation (returns immediately)
curl -X POST "http://localhost:8080/api/simulation/run?ratePerSecond=2&minPickupSec=4&maxPickupSec=8"
```

---

## Configuration

`application.yaml` (example):

```yaml
server:
  port: 8080

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/kitchen
    username: postgres
    password: postgres
  flyway:
    enabled: true

challenge:
  base-url: "https://host/interview/challenge" # full prefix required
  auth-token: "your-token"
  cli:
    enabled: false
```

If you don’t use Flyway, disable it or remove migration deps.

**DB schema** (simplified for reference):

```sql
CREATE TABLE IF NOT EXISTS actions (
  id BIGSERIAL PRIMARY KEY,
  ts TIMESTAMP NULL,
  order_id VARCHAR(128) NOT NULL,
  action VARCHAR(32) NOT NULL,
  target VARCHAR(32) NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
  id VARCHAR(128) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  temp VARCHAR(16) NOT NULL,
  storage VARCHAR(16),
  freshness INTEGER,
  placed_at TIMESTAMP
);
```

---

## Build & Run

### Prereqs
- Java 21
- Docker (for Testcontainers)
- PostgreSQL (running) if you run locally outside of tests

### Gradle

```bash
# Build
./gradlew clean build

# Run app
./gradlew bootRun
```

### CLI simulation mode

Enable CLI runner and block until completion:

```bash
java -jar build/libs/kitchen-*.jar   --challenge.cli.enabled=true   --rate=2 --min=4 --max=8   --challenge.base-url="https://host/interview/challenge"   --challenge.auth-token="TOKEN"
```

Exit code **0** on success, **1** on failure.

---

## Tests

### Unit tests
- `StorageServiceTest` — capacities, moves, shelf discard, expiry logic.
- `KitchenServiceTest` — orchestration (place/move/discard/pickup) with mocks.
- Mapper tests — Instant→micros, DTO mappings.

Run:

```bash
./gradlew test
```

### Integration tests
- `KitchenControllerIT` — REST flow via `WebTestClient`, real Postgres (Testcontainers).
- `KitchenSimulatorIT` — end-to-end with **WireMock** for `/new` & `/solve`.

Docker must be running; Postgres container is bootstrapped automatically.

---

## Key design notes

- **Time:** microsecond precision for decay and outbound timestamps; seconds for order freshness.
- **Concurrency:** `ReentrantLock` per storage; blocking operations offloaded with `Schedulers.boundedElastic()`.
- **Shelf discard:** min-heap by predicted expiry (O(log n)).
- **Error handling:**
    - Simulator filters actions strictly within the current run window `[start; end]` (μs).
    - Pickup: if storage removal fails, action is not written (avoid false ledger entries).
    - Discard: delete errors are swallowed to keep the stream hot.

---

## Troubleshooting

- **Testcontainers — “Mapped port can only be obtained after the container is started”**  
  Use `@DynamicPropertySource` or `@ServiceConnection`; don’t read ports in an initializer before `PG.start()`.

- **Mockito ByteBuddy warning on JDK 21**  
  Add `-XX:+EnableDynamicAgentLoading` to test JVM args if you want to silence the notice.

- **Web vs WebFlux**  
  Project uses **WebFlux**. Use `springdoc-openapi-starter-webflux-ui`.

---

## Project structure (suggested)

```
src/main/java
  ├─ controller/KitchenController.java
  ├─ service/KitchenService.java
  ├─ service/StorageService.java
  ├─ service/KitchenSimulator.java
  ├─ mapper/{ActionMapper,OrderMapper,OrderEntityMapper,ActionEntityMapper}.java
  ├─ model/{Order,OrderEntity,ActionEntity,Enums,DTOs}.java
src/test/java
  ├─ storage/StorageServiceTest.java
  ├─ service/KitchenServiceTest.java
  ├─ mapper/*MapperTest.java
  ├─ it/{AbstractR2dbcIT,KitchenControllerIT,KitchenSimulatorIT}.java
openapi.yaml
```

---

## License

Add your preferred license here.
