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

`./gradlew bootRun` requires PostgreSQL 16.2 reachable at `localhost:5433` (non-standard port), database `store`, user/pass `admin:admin`:

```shell
docker run -d --name postgres --restart always \
  -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=store \
  -v postgres:/var/lib/postgresql/data -p 5433:5432 \
  postgres:16.2 postgres -c wal_level=logical
```

`./gradlew test` does not need that container — repository/integration tests manage their own Postgres 16.2 via Testcontainers (see Testing) — but it does need a working Docker Engine (not just the `docker` CLI; see the Testcontainers note under Testing for a Windows/Docker Desktop gotcha).

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

`spring.datasource.{url,username,password}` follow the same locally-defaulted-but-overridable pattern as `app.security.api-key`: the committed defaults (`admin`/`admin` on `localhost:5433`) match the `docker run` command in the README/this file's Commands section, but every other environment should set `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` rather than relying on the checked-in default.

**Table naming quirk.** The `Order` entity maps to a quoted table name (`@Table(name = "\"order\"")`) because `order` is a reserved SQL keyword — required whenever writing raw SQL/Liquibase changesets against it.

**API versioning.** All business endpoints live under `/v1` (`/v1/customer`, `/v1/order`, `/v1/products` — set via each controller's `@RequestMapping`, not a global `server.servlet.context-path`, so it doesn't also prefix `/actuator/**`). `/actuator/**` is deliberately unversioned — it's operational tooling, not part of the versioned business API contract. A breaking API change belongs in a new `/v2` set of controllers (or a version-specific subset), not a change to `/v1` in place.

**DB resilience.** Every service method carries `@CircuitBreaker(name = "database")`; read methods additionally carry `@Retry(name = "database")` — writes deliberately do **not** auto-retry, since a connection dropping after Postgres receives the write but before the ack comes back would make a blind retry insert a duplicate row (reads have no such risk). Both hierarchies matter when configuring which exceptions count: a failed query throws `DataAccessException`, but failing to even open the transaction (no connection available) throws `TransactionException` — a sibling hierarchy, not a subtype. `GlobalExceptionHandler` (see below) translates `CallNotPermittedException` (circuit open) and both exception hierarchies into a clean `503`. `spring.datasource.hikari.connection-timeout` is set explicitly (5s) so a dead DB fails fast rather than hanging on the pool's default timeout. Tune thresholds in `application.yaml` under `resilience4j.circuitbreaker.instances.database` / `resilience4j.retry.instances.database`. `resilience/ResilienceEventLogging` logs circuit breaker state transitions (CLOSED/OPEN/HALF_OPEN) as they happen, independent of any single request.

**Exception handling.** `exception/GlobalExceptionHandler` is the single source of every error response's shape: RFC 7807 `ProblemDetail` (`type`/`title`/`status`/`detail`/`instance`/`timestamp`), for every exception that reaches it — business validation (`ResponseStatusException`), DB failures, malformed request bodies, and anything unhandled. Not every `DataAccessException` means the DB is down: a constraint violation is a data problem (`409`), not infrastructure unavailability (`503`) — see `isConnectivityFailure`. Two error paths can't be intercepted by `@RestControllerAdvice` because they happen in a servlet filter before Spring MVC dispatch — `ApiKeyAuthFilter`'s `401` (built in `SecurityConfig`'s `authenticationEntryPoint`) and `IdempotencyFilter`'s `409` — both build the identical `ProblemDetail` shape independently via the same injected `ObjectMapper`; keep them in sync with `GlobalExceptionHandler` if the shape ever changes. A known, separate, unfixed quirk: a genuinely unmapped path returns `401` instead of `404` regardless of API key validity, because Spring Boot's internal error-forward for "no handler found" re-runs Spring Security's authorization filter without re-running `ApiKeyAuthFilter` (a `OncePerRequestFilter`, correctly doesn't re-execute on the same underlying request) — doesn't affect any real endpoint, not worth the complexity to fix.

**Logging.** `logging/RequestIdFilter` (first in the security filter chain, so it covers rejected/unauthenticated requests too) tags every request with a correlation id — from the caller's `X-Request-Id` header if provided, otherwise generated — via MDC, echoed back in the response header. Local `bootRun` logs plain text with `[%X{requestId}]` in the pattern; structured (JSON) logging is Spring Boot 3.4's built-in support (`logging.structured.format.console`, unset locally, defaulted to `ecs` in the Dockerfile for containers) and automatically includes all MDC content with no extra config. Log at the level that matches who should care: `INFO` for routine 4xx business outcomes (a client asked for something that doesn't exist - not a server problem), `WARN` for anomalies worth an operator's attention (auth rejections, idempotency conflicts, circuit breaker events), `ERROR` for genuine failures (unhandled exceptions, DB connectivity failures). Follow this pattern in new filters/handlers rather than failing silently.

**Security.** All endpoints require an `X-API-Key` header matching `app.security.api-key` (`API_KEY` env var; defaults to `local-dev-only-change-me` for local dev — see `application.yaml`), enforced by `ApiKeyAuthFilter` + `SecurityConfig` in `security/`. The one deliberate exception is `/actuator/health/**`, which stays open since container orchestrators and the CI smoke test poll it without sending credentials. `@WebMvcTest` controller tests disable the security filter chain via `@AutoConfigureMockMvc(addFilters = false)` since they test controller/mapping logic, not auth — auth itself is covered by `ApiKeyAuthFilterTest`. When manually hitting the API (curl, Postman, etc.), remember to pass the header.

**Idempotency.** POST endpoints accept an optional `Idempotency-Key` header (see `idempotency/IdempotencyFilter`); a retry with the same key on the same path replays the original response instead of repeating the write. Backed by the `idempotency_record` table (not an in-memory map) with a unique constraint on `(idempotency_key, request_path)`, so it's correct across multiple instances and under real concurrency — a second request racing the same key gets `409` rather than duplicating the write. The header is optional: omit it and POSTs behave exactly as before (not idempotent). `5xx` responses are deliberately *not* cached under the key (the placeholder row is deleted instead), so a transient failure doesn't permanently lock a client out of retrying. Runs in the security filter chain, positioned after `ApiKeyAuthFilter` so it only touches the DB for requests that are already authenticated.

**Environments (dev/qa/prod) & deployment.** Since app config is already fully 12-factor externalized (`SPRING_DATASOURCE_*`, `API_KEY`, `LOGGING_STRUCTURED_FORMAT_CONSOLE`, `APP_ENVIRONMENT`), the per-environment differences live entirely in *values* supplied at deploy time, not in Spring profiles or environment-specific YAML files in this repo. `APP_ENVIRONMENT` is surfaced at `GET /actuator/info` (`info.environment` in `application.yaml`) purely so a curl against a running instance immediately shows which environment it is — defaults to `local` for `bootRun`/tests. `.github/workflows/ci.yml` builds the Docker image once per commit on `master`, smoke-tests it, and tags/pushes it to `ghcr.io` by immutable commit SHA only (no `latest`). There are long-lived `dev`/`qa`/`prod` branches (created from `master`); pushing/fast-forwarding one of them to a commit is what promotes it — the matching `deploy-dev`/`deploy-qa`/`deploy-prod` job re-points that environment's moving tag (`:dev`/`:qa`/`:prod`) at the commit-SHA image already built and smoke-tested on `master`, rather than rebuilding from source, so what was tested is byte-for-byte what ships. That image is published by a separate, independently-triggered workflow run (the `master` push), so pulling it races against master's own build+smoke-test possibly still being in flight — expected when promoting shortly after a merge, not a bug — so the pull retries for ~5 minutes before giving up rather than failing on the first miss; a pull that never succeeds means this commit genuinely never went through the `master` pipeline, which is the intended gate — only a commit that already passed CI can be promoted, not source pushed fresh to an environment branch. Promotion order (dev before qa before prod) is a process convention, not pipeline-enforced, since each branch push is an independent run. Each `deploy-*` job is gated by a GitHub Environment (`environment: dev|qa|prod`) — this is what unlocks environment-scoped secrets/variables and protection rules (required reviewers, branch policy, wait timers) in GitHub; `prod`'s branch policy is intentionally restricted to the `prod` branch itself, which is exactly what makes branch-triggered (rather than master-triggered) promotion necessary — a `deploy-prod` run on `master` can never satisfy that policy. This gating is configured once by a repo admin under Settings → Environments, not expressible in the workflow YAML or something CI should set up unattended. Each environment's actual config values (`SPRING_DATASOURCE_URL`, `API_KEY`, etc.) belong in that environment's own GitHub Environment secrets/variables once there's a real deploy step to consume them — the current `deploy-*` jobs only retag/push the image (no real deploy target exists in this repo yet); replace the "retag and push" step with the real deployment command (`kubectl set image`, `aws ecs update-service`, `helm upgrade`, ...) when one does.

## Code style

Enforced via Spotless (`palantirJavaFormat`, PALANTIR style) — run `spotlessApply` before committing. Import order is fixed: `com`, `jakarta`, `lombok`, `org`, `` (blank/other), `javax|java`, then static imports (`\#`). Unused imports are stripped automatically; don't hand-tune import grouping.

Lombok `@Data` is used on entities/DTOs in place of hand-written getters/setters/equals/hashCode — follow suit rather than writing boilerplate accessors.

## Testing

Controller tests use `@WebMvcTest` (slice test, not full context) with `@MockitoBean` to stub the service layer, plus `@ComponentScan(basePackageClasses = ...)` to pull in the real MapStruct mapper bean rather than mocking it. Follow this pattern — mock the service, let the real mapper run — for new controller tests.

Controller tests also carry `.andExpect(openApi().isValid(validator()))` (see `OpenApiContractSupport`, `swagger-request-validator-mockmvc`) on top of the usual `jsonPath` assertions — this validates the actual response against `OpenAPI.yaml` itself, not just the hand-written expectations in the test, so the spec can't silently drift from what the service actually returns. Add this to any new controller test that hits a documented endpoint. `X-API-Key` is included on requests even though `addFilters = false` disables enforcement, so the request is fully spec-compliant for validation purposes. The validator loads `OpenAPI.yaml` via a relative path from the project root, which Gradle's `test` task uses as its working directory by default.

Service tests are plain JUnit + Mockito (no Spring context at all) with `@MockitoBean`-equivalent `@Mock` repositories injected via `@InjectMocks` (or constructed directly) — business rules and transactional orchestration should be verifiable without spinning up any Spring machinery.

`PostgresTestContainer` (directly under `src/test/java/com/example/store/`) is one Postgres 16.2 Testcontainers container shared by every test that needs a real database, started once per JVM in a static initializer and never explicitly stopped (Testcontainers' Ryuk sidecar reaps it at JVM exit) — this is Testcontainers' own documented "singleton container" pattern. Deliberately not `@Testcontainers`/`@Container`: that JUnit5-extension-managed lifecycle is scoped per test class and doesn't reliably keep one container alive across unrelated test classes sharing it only via inheritance — using it here caused intermittent "connection refused" failures between repository test classes in CI (`OrderRepositoryTest`/`ProductRepositoryTest` failing right after `CustomerRepositoryTest` had already passed against what should have been the same container). Any new test class that needs a real database should `extend PostgresTestContainer` rather than declaring its own container.

Repository tests (`repository/*RepositoryTest`, via `AbstractRepositoryTest extends PostgresTestContainer`) use `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = Replace.NONE)` so Spring doesn't substitute an embedded database — the hand-written `JOIN FETCH` JPQL and the `"order"` reserved-keyword table mapping aren't guaranteed to behave the same against H2. Liquibase runs its full changelog (schema + ~10k rows of bulk sample data + sequence sync) against the container on context startup, same as any other fresh database. To prove a fetch-join query actually eager-loads its association (rather than the test's own first-level cache masking a real N+1), persist via `TestEntityManager`, call `entityManager.clear()` to detach everything, then assert `Hibernate.isInitialized(...)` on the association after calling the repository method.

`integration/SecurityFilterChainIntegrationTest` (also `extends PostgresTestContainer`) boots the real `@SpringBootTest(webEnvironment = RANDOM_PORT)` context — unlike `@WebMvcTest`, it does not disable the security filter chain — and asserts on the actual filter order (e.g. `X-Request-Id` is present even on a `401`, proving `RequestIdFilter` really does run before authentication is decided). This exists because a wrong relative filter order in `SecurityConfig` previously crashed `bootRun` at startup while every filter's own isolated unit test stayed green — no test-in-isolation can catch a wiring/ordering mistake between filters, only a real end-to-end boot can. When asserting on a response's `Content-Type` header, compare with `MediaType.isCompatibleWith(...)`, not `isEqualTo(...)` — the servlet container can append a charset parameter that a strict equality check doesn't expect.

Testcontainers requires a working Docker Engine API connection to run these tests, which is separate from having the `docker` CLI work — on some Windows/Docker Desktop configurations the CLI works (it goes through Docker Desktop's own proxy) while Testcontainers' Java client cannot reach a genuine Engine API endpoint over any of the named pipes. If `./gradlew test` fails these specific classes locally with `Could not find a valid Docker environment`, that's most likely this, not a code problem — CI (`build-and-test` job, GitHub Actions' Linux runners) talks to a real Docker daemon over a Unix socket and isn't affected.
