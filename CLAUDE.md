# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A Spring Boot service that tracks customers and orders. This repository is a take-home coding exercise (see "Tasks" below) built on a deliberately minimal/imperfect skeleton — the README explicitly notes "bad choices were deliberately made when creating this project" and invites refactoring.

Data model: a `Customer` has an ID, a name, and 0+ `Order`s; an `Order` has an ID, a description, and belongs to one `Customer`. The relationship is bidirectional in JPA.

### Outstanding tasks (from README)

These describe the intended scope of work in this repo — check current code against them before assuming a task is done:

1. Add a "find order by ID" endpoint.
2. Add customer search by substring match against words in their name.
3. GET endpoints are reportedly slow in production because the DB has high latency from the app server — look for and fix N+1 / over-fetching issues.
4. Add a `/products` endpoint: a product has an ID and description; an order contains 1+ products. POST to create; GET all and GET by ID (each returning associated order IDs); and the orders endpoint should also return the products in each order. `OpenAPI.yaml` already documents the intended shape of this endpoint — it does not yet reflect an implementation.
5. (Bonus) CI pipeline that builds and ships a Docker image.

## Commands

Requires PostgreSQL 16.2 reachable at `localhost:5433` (non-standard port), database `store`, user/pass `admin:admin`:

```shell
docker run -d --name postgres --restart always \
  -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=store \
  -v postgres:/var/lib/postgresql/data -p 5433:5432 \
  postgres:16.2 postgres -c wal_level=logical
```

On Windows use `gradlew.bat` in place of `./gradlew`.

```shell
./gradlew bootRun                     # run the app (applies Liquibase migrations on startup)
./gradlew test                        # run all tests (JUnit 5 via JUnitPlatform)
./gradlew test --tests "com.example.store.controller.CustomerControllerTests"                  # single test class
./gradlew test --tests "com.example.store.controller.CustomerControllerTests.testCreateCustomer" # single test method
./gradlew jacocoTestReport            # coverage report (also runs automatically after `test`); HTML output under build/reports/jacoco
./gradlew spotlessCheck               # verify formatting
./gradlew spotlessApply               # auto-fix formatting
```

Coverage reporting excludes the `mapper` package and `StoreApplication` (see `build.gradle`).

To regenerate bulk sample data (writes straight into the Liquibase data changeset):

```shell
cd utils && npm install
node ./generateData.js > ../src/main/resources/db/changelog/data.sql
```

If the Liquibase changelog itself changes, existing local databases need manual changelog surgery or a drop/recreate (see `utils/README.md`).

## Architecture

**Service layer.** `Controller → Service → Repository (JpaRepository) → Entity`, with MapStruct mapping `Entity → DTO` at the controller boundary (services operate on entities, not DTOs). Services own transaction boundaries (`@Transactional`) and business-rule validation (e.g. rejecting an order with an unknown customer/product id); controllers only translate HTTP in/out and shouldn't call repositories directly. When extending existing endpoints or adding new ones, follow this same controller→service→repository pattern.

**DTO pairing breaks entity cycles.** `Customer` and `Order` reference each other (`Customer.orders` / `Order.customer`), so serializing an entity graph directly would loop. Each entity has two DTOs:
- A "full" outer DTO returned from an endpoint (`CustomerDTO`, `OrderDTO`) that embeds the *other* side as a truncated form.
- A truncated "back-reference" DTO (`CustomerOrderDTO` on `CustomerDTO.orders`, `OrderCustomerDTO` on `OrderDTO.customer`) that omits the far side entirely, stopping the cycle.

Mapping is done via MapStruct interfaces in `mapper/` (`@Mapper(componentModel = "spring")`), which generate Spring-managed mapper beans at compile time — there are no hand-written mapper implementations to look for. Any new entity relationship exposed over the API should follow this same full/truncated DTO pairing rather than serializing entities directly.

**Persistence.** `spring.jpa.hibernate.ddl-auto` is `validate` — schema is owned by Liquibase (`db/changelog/db.changelog-master.yaml` → `db.changelog-1.yaml` → `schema.sql`, `db.changelog-2.yaml` → `data.sql`), not Hibernate auto-DDL. New schema changes belong in a new Liquibase changeset, not in entity annotations alone. `hibernate.default_batch_fetch_size` is set to 10 to batch lazy-association fetches; keep this in mind (and prefer batching/fetch-join fixes over flipping associations to `EAGER`) when addressing the "slow GET endpoints under high DB latency" task.

**Table naming quirk.** The `Order` entity maps to a quoted table name (`@Table(name = "\"order\"")`) because `order` is a reserved SQL keyword — required whenever writing raw SQL/Liquibase changesets against it.

**API versioning.** All business endpoints live under `/v1` (`/v1/customer`, `/v1/order`, `/v1/products` — set via each controller's `@RequestMapping`, not a global `server.servlet.context-path`, so it doesn't also prefix `/actuator/**`). `/actuator/**` is deliberately unversioned — it's operational tooling, not part of the versioned business API contract. A breaking API change belongs in a new `/v2` set of controllers (or a version-specific subset), not a change to `/v1` in place.

**DB resilience.** Every service method carries `@CircuitBreaker(name = "database")`; read methods additionally carry `@Retry(name = "database")` — writes deliberately do **not** auto-retry, since a connection dropping after Postgres receives the write but before the ack comes back would make a blind retry insert a duplicate row (reads have no such risk). Both hierarchies matter when configuring which exceptions count: a failed query throws `DataAccessException`, but failing to even open the transaction (no connection available) throws `TransactionException` — a sibling hierarchy, not a subtype. `DatabaseUnavailableExceptionHandler` (in `resilience/`) translates `CallNotPermittedException` (circuit open) and both exception hierarchies into a clean `503` instead of a raw `500`. `spring.datasource.hikari.connection-timeout` is set explicitly (5s) so a dead DB fails fast rather than hanging on the pool's default timeout. Tune thresholds in `application.yaml` under `resilience4j.circuitbreaker.instances.database` / `resilience4j.retry.instances.database`.

**Security.** All endpoints require an `X-API-Key` header matching `app.security.api-key` (`API_KEY` env var; defaults to `local-dev-only-change-me` for local dev — see `application.yaml`), enforced by `ApiKeyAuthFilter` + `SecurityConfig` in `security/`. The one deliberate exception is `/actuator/health/**`, which stays open since container orchestrators and the CI smoke test poll it without sending credentials. `@WebMvcTest` controller tests disable the security filter chain via `@AutoConfigureMockMvc(addFilters = false)` since they test controller/mapping logic, not auth — auth itself is covered by `ApiKeyAuthFilterTest`. When manually hitting the API (curl, Postman, etc.), remember to pass the header.

**Idempotency.** POST endpoints accept an optional `Idempotency-Key` header (see `idempotency/IdempotencyFilter`); a retry with the same key on the same path replays the original response instead of repeating the write. Backed by the `idempotency_record` table (not an in-memory map) with a unique constraint on `(idempotency_key, request_path)`, so it's correct across multiple instances and under real concurrency — a second request racing the same key gets `409` rather than duplicating the write. The header is optional: omit it and POSTs behave exactly as before (not idempotent). `5xx` responses are deliberately *not* cached under the key (the placeholder row is deleted instead), so a transient failure doesn't permanently lock a client out of retrying. Runs in the security filter chain, positioned after `ApiKeyAuthFilter` so it only touches the DB for requests that are already authenticated.

## Code style

Enforced via Spotless (`palantirJavaFormat`, PALANTIR style) — run `spotlessApply` before committing. Import order is fixed: `com`, `jakarta`, `lombok`, `org`, `` (blank/other), `javax|java`, then static imports (`\#`). Unused imports are stripped automatically; don't hand-tune import grouping.

Lombok `@Data` is used on entities/DTOs in place of hand-written getters/setters/equals/hashCode — follow suit rather than writing boilerplate accessors.

## Testing

Controller tests use `@WebMvcTest` (slice test, not full context) with `@MockitoBean` to stub the service layer, plus `@ComponentScan(basePackageClasses = ...)` to pull in the real MapStruct mapper bean rather than mocking it. Follow this pattern — mock the service, let the real mapper run — for new controller tests.

Service tests are plain JUnit + Mockito (no Spring context at all) with `@MockitoBean`-equivalent `@Mock` repositories injected via `@InjectMocks` (or constructed directly) — business rules and transactional orchestration should be verifiable without spinning up any Spring machinery.
