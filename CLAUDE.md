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

**No service layer.** Controllers talk directly to Spring Data JPA repositories:
`Controller → Repository (JpaRepository) → Entity`, with MapStruct mapping `Entity → DTO` at the controller boundary. When extending existing endpoints or adding new ones (e.g. `/products`), follow this same direct controller→repository pattern unless there's a specific reason to introduce a service layer.

**DTO pairing breaks entity cycles.** `Customer` and `Order` reference each other (`Customer.orders` / `Order.customer`), so serializing an entity graph directly would loop. Each entity has two DTOs:
- A "full" outer DTO returned from an endpoint (`CustomerDTO`, `OrderDTO`) that embeds the *other* side as a truncated form.
- A truncated "back-reference" DTO (`CustomerOrderDTO` on `CustomerDTO.orders`, `OrderCustomerDTO` on `OrderDTO.customer`) that omits the far side entirely, stopping the cycle.

Mapping is done via MapStruct interfaces in `mapper/` (`@Mapper(componentModel = "spring")`), which generate Spring-managed mapper beans at compile time — there are no hand-written mapper implementations to look for. Any new entity relationship exposed over the API should follow this same full/truncated DTO pairing rather than serializing entities directly.

**Persistence.** `spring.jpa.hibernate.ddl-auto` is `validate` — schema is owned by Liquibase (`db/changelog/db.changelog-master.yaml` → `db.changelog-1.yaml` → `schema.sql`, `db.changelog-2.yaml` → `data.sql`), not Hibernate auto-DDL. New schema changes belong in a new Liquibase changeset, not in entity annotations alone. `hibernate.default_batch_fetch_size` is set to 10 to batch lazy-association fetches; keep this in mind (and prefer batching/fetch-join fixes over flipping associations to `EAGER`) when addressing the "slow GET endpoints under high DB latency" task.

**Table naming quirk.** The `Order` entity maps to a quoted table name (`@Table(name = "\"order\"")`) because `order` is a reserved SQL keyword — required whenever writing raw SQL/Liquibase changesets against it.

## Code style

Enforced via Spotless (`palantirJavaFormat`, PALANTIR style) — run `spotlessApply` before committing. Import order is fixed: `com`, `jakarta`, `lombok`, `org`, `` (blank/other), `javax|java`, then static imports (`\#`). Unused imports are stripped automatically; don't hand-tune import grouping.

Lombok `@Data` is used on entities/DTOs in place of hand-written getters/setters/equals/hashCode — follow suit rather than writing boilerplate accessors.

## Testing

Controller tests use `@WebMvcTest` (slice test, not full context) with `@MockitoBean` to stub repositories, plus `@ComponentScan(basePackageClasses = ...)` to pull in the real MapStruct mapper bean rather than mocking it. Follow this pattern — mock the repository layer, let the real mapper run — for new controller tests.
