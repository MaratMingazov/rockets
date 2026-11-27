# Working on this project

Instructions for AI coding assistants (and a summary for people). Read `README.md` first: it describes the task,
the API, the architecture, failure handling and the trade-offs.

The service consumes rocket messages that arrive out of order and at least once, applies them per rocket in
`messageNumber` order (event log + cursor + PostgreSQL row lock) and exposes the current state via REST.
Kotlin, Spring Boot 4, PostgreSQL 17, Flyway, Spring JDBC (`JdbcClient`), JUnit 5 + Testcontainers.

## Commands

```bash
./gradlew test                                        # all tests; needs JDK 21 and a running Docker (Testcontainers)
docker compose --profile monitoring down -v           # clean state: containers + database volume
docker compose --profile monitoring up -d --build     # PostgreSQL, the service (:8088), Prometheus, Grafana (:3000)
docker compose up -d postgres && ./gradlew bootRun    # development: only the database in Docker
./task/darwin_arm64/rockets launch "http://localhost:8088/messages" --message-delay=100ms --concurrency-level=4 --max-messages=2000 # run the test program
```

The test program (`./rockets launch "http://localhost:8088/messages"`, from the challenge ZIP, not in the repository)
and the application are **run by the developer**, not by the assistant. The assistant may run `./gradlew test`,
builds, and read-only queries against the local Prometheus / PostgreSQL to analyse a run.

## How we work

- **Design first.** Before writing code, discuss the approach and record the decision (what, why, alternatives
  considered) together with any open questions. Do not start implementing an undecided design.
- **Facts over assumptions.** If behaviour is unclear — of the test program, a library or the system under load —
  measure it (a temporary logging endpoint, a test, a Prometheus or SQL query) instead of guessing.
  Report numbers with the conditions they were measured under.
- **Small steps, each verified.** One layer or concern at a time, with its tests, and all tests green before moving on.
  Explain what changed and why after each step.
- **Check APIs against the real libraries.** Spring Boot 4 and Jackson 3 moved packages and renamed starters and
  properties (`tools.jackson.*`, `spring-boot-starter-webmvc`, `org.springframework.boot.webmvc.test.autoconfigure`,
  …). Verify dependency names, annotations and properties in the jars / configuration metadata, not from memory.
- **No scope creep.** Propose additions (brokers, extra services, pagination, plugins) with their cost; implement only
  after the developer agrees.

## What to delegate, what the developer owns

| The assistant may | The developer decides or does |
|---|---|
| Draft code and tests for a step that was agreed beforehand | The design and every decision; reviewing each change |
| Write boilerplate: Gradle, Dockerfile, compose, Grafana dashboard JSON | Running the service and the test program, reading logs and dashboards |
| Draft documentation | What goes into the README and what is in or out of scope |
| Query Prometheus / PostgreSQL to summarise a run | Whether a run is valid (clean database, correct targets) and what it means |

## Code structure and conventions

- Packages: `api` (HTTP, DTOs, Problem Details) → `service` (transactions) → `domain` (pure Kotlin) /
  `persistence` (SQL); `codec` is shared by `api` and `persistence`.
- `domain` has no Spring and no Jackson. All state transition rules live in `Rocket.applyMessage`.
- `api` rejects invalid input; the service receives only typed, validated `ReceivedMessage`s.
- Repositories contain SQL only, no business logic. Explicit SQL via `JdbcClient`, no JPA. Values are always bound
  as parameters; dynamic SQL fragments (sort columns) come from enums, never from client input.
- Write methods that rely on the row lock are `@Transactional(propagation = MANDATORY)`.
- Schema changes go into a new Flyway migration; never edit one that may have been applied.
- Comments: KDoc on the public API of key classes and short "why" comments on non-obvious decisions only.
  No comments that restate the code, none in config or SQL.

## Invariants — do not break

- Every applied message advances the cursor (`last_applied_message_number`), even if its event is ignored.
- Lock the rocket row first, then insert the event.
- `202` only after the transaction has committed; any failure rolls back everything.
- Duplicates are dropped by the cursor (applied) or the primary key (buffered); the first stored message wins.
- `PENDING` rockets are not exposed by the API.
- Every error response is RFC 9457 Problem Details; `500` never exposes internal details.

## Tests

- Unit tests for domain rules and parsing; `@WebMvcTest` for the HTTP contract; `@JdbcTest` + Testcontainers for SQL;
  `@SpringBootTest` + Testcontainers for scenarios and concurrency.
- In scenario and concurrency tests the expected state is `Flights.expected(...)`: the same messages applied once,
  in order, with the domain function.
- For changes to locking, transactions or error handling, check that the relevant test **fails** when the protection
  is removed (e.g. without `FOR UPDATE`), then restore it.

## Definition of done for a step

- `./gradlew test` is green, with no new compiler warnings.
- The README is updated if behaviour, commands or numbers changed; every claim in it is backed by a test or a measurement.
- The developer has reviewed the change.
