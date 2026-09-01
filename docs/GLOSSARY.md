# Glossary

The vocabulary used in this codebase and in enterprise Java interviews. Each
entry says what the term means and, where it applies, points at the file in this
project that demonstrates it.

---

## Persistence (JPA / Hibernate)

**Entity** — a class mapped to a database table, with an identity that survives
change. `@Entity`. → `domain/model/Student.java`

**Value object** — a type with no identity; it *is* its value, like `Integer`.
Immutable. Persisted with `@Embeddable`. → `domain/model/Email.java`

**Embeddable** — a value object whose columns are stored inside the owning
entity's table. No table, no id of its own.

**Association entity** (also *join entity*, *link entity*) — an entity that
exists because a relationship carries data of its own. → `Enrollment`

**Aggregate / aggregate root** — a cluster of objects treated as one unit for
persistence and consistency. `Student` is the root of `Student → Enrollment`,
which is why the cascade goes that way.

**Surrogate key** — a meaningless generated id (`id BIGINT`). The default choice.
**Natural key** — a key with business meaning (`studentNumber`). Used for
`equals`/`hashCode` where one exists.

**Persistence context** — the EntityManager's in-memory cache of managed
entities. The "first-level cache".

**Managed / detached / transient** — the three entity states. *Transient* = new,
unknown to JPA. *Managed* = tracked by a persistence context. *Detached* = was
managed, no longer is (its context closed).

**Dirty checking** — JPA compares managed entities against their loaded snapshot
at commit and writes an UPDATE for whatever changed. You never call `update()`.
→ `EnrollmentService.recordPass`

**`persist` vs `merge`** — `persist` makes a *new* entity managed (same
instance). `merge` copies a *detached* entity's state onto a managed instance and
**returns that instance**. Ignoring the return value of `merge` is a classic bug.

**Lazy loading** — an association is a placeholder until first touched.
**Eager loading** — always loaded. Make everything lazy and fetch per use case.

**Proxy** — the placeholder object Hibernate substitutes for a lazy association.
It is a generated subclass, which is why `equals` must use `instanceof`, not
`getClass() ==`.

**`LazyInitializationException`** — you touched a lazy association after the
persistence context closed. The fix is a fetch plan, *not* making it eager.

**N+1 select problem** — 1 query for a list, then 1 more per row. The single most
common JPA performance bug. Cured with `JOIN FETCH` or a batch query.
→ `EnrollmentRepository.countOccupiedSeatsByCourse`

**Fetch plan** — the deliberate decision, per use case, about which associations
a query loads.

**JPQL** — object-oriented query language over entities, not tables.
**Criteria API** — the type-safe, programmatic equivalent, for dynamic queries.
→ `StudentRepository.search`

**Named query** — JPQL declared on the entity and validated at **deploy time**, so
a typo fails the deployment rather than a user's request.

**Optimistic locking** — `@Version`; detect conflicts at commit. Scales well.
**Pessimistic locking** — `SELECT … FOR UPDATE`; block other writers. Correct but
serialising. → `CourseRepository.findByIdWithPessimisticLock`

**Lost update** — two transactions read, both write, the first change vanishes.
What `@Version` prevents.

**Bulk operation** — one `UPDATE … WHERE`, bypassing the persistence context.
Fast, but skips lifecycle callbacks and leaves loaded entities stale.

**Second-level cache** — a cache shared across persistence contexts. Disabled
here to keep the mental model simple.

**Open Session In View** — keeping the persistence context open during response
rendering so lazy loads still work. An anti-pattern: it hides N+1 problems and
holds resources during I/O.

---

## Dependency injection (CDI)

**CDI** — Contexts and Dependency Injection. Decides which classes are beans,
creates them, injects them, destroys them.

**Bean** — a class the container manages.
**Scope** — how long an instance lives: `@ApplicationScoped` (one for the app),
`@RequestScoped`, `@SessionScoped`, `@Dependent` (lives with its injector).

**Normal scope vs pseudo-scope** — normal scopes (`@ApplicationScoped`,
`@RequestScoped`) are injected as a **client proxy**, which requires a
non-private no-arg constructor. `@Dependent` is a pseudo-scope and is not
proxied — which is why `ClockProducer` uses it.

**Producer method** — `@Produces`; a factory for types CDI cannot construct
itself, such as third-party classes. → `common/logging/LoggerProducer.java`

**`InjectionPoint`** — metadata about *where* something is being injected. Lets
one producer give every class a logger named after that class.

**Qualifier** — an annotation that distinguishes two beans of the same type.

**Interceptor / interceptor binding** — Jakarta EE's aspect-oriented
programming. The binding is the marker annotation; the interceptor is the
behaviour. → `common/logging/Loggable.java`, `LoggingInterceptor.java`

**`@Priority`** — enables an interceptor globally and fixes its position in the
chain. Lower runs further out.

**Self-invocation trap** — interceptors work through a proxy, so a method calling
another annotated method **on the same instance** bypasses the interceptor
entirely. Catches everyone once, in both Jakarta EE and Spring.

**CDI event / observer** — `Event<T>.fire()` and `@Observes`. Decouples "what
happened" from "what should happen about it". → `EnrollmentNotificationListener`

**`TransactionPhase`** — when an observer runs relative to the transaction.
`AFTER_SUCCESS` is the right choice for external side effects like email.

---

## Transactions (JTA)

**ACID** — Atomicity, Consistency, Isolation, Durability.

**`@Transactional`** — marks a transaction boundary. The container begins,
commits, or rolls back around the method.

**Propagation** — `REQUIRED` (default: join or start), `REQUIRES_NEW` (always
fresh), `MANDATORY` (must already be in one), `SUPPORTS`, `NOT_SUPPORTED`,
`NEVER`.

**Rollback rules** — an **unchecked** exception rolls back; a **checked**
exception **commits** unless you declare `rollbackOn`. The reason this project's
exceptions are unchecked.

**JTA vs RESOURCE_LOCAL** — container-managed transactions versus
`em.getTransaction().begin()` by hand. Production uses JTA; the H2 tests use
RESOURCE_LOCAL because there is no container.

**Serialisation point** — a row deliberately locked so that concurrent operations
on a resource are ordered. The `courses` row plays this role for its own capacity.

---

## REST (JAX-RS)

**Resource** — a class serving a URI, annotated `@Path`.
**Sub-resource** — a nested path expressing ownership: `/students/42/enrollments`.

**`@BeanParam`** — groups related query/path/header parameters into one class.
→ `api/dto/PaginationParams.java`

**Content negotiation** — `@Produces` / `@Consumes` plus the `Accept` and
`Content-Type` headers.

**Provider** — a JAX-RS extension: `@Provider` on a filter, an exception mapper,
or a message body reader/writer.

**Filter vs interceptor (JAX-RS)** — *filters* see headers, URIs and status codes
(auth, CORS, logging). *Interceptors* wrap the entity stream (compression,
encryption). Different from CDI interceptors.

**`@PreMatching`** — run a request filter *before* routing, so even unmatched
requests are covered.

**Exception mapper** — one handler per exception type, replacing try/catch in
every endpoint. → `api/exception/`

**RFC 7807 / Problem Details** — the standard error body shape:
`type`, `title`, `status`, `detail`, `instance`. → `api/dto/response/ProblemDetail.java`

**Idempotent** — applying it twice has the same effect as once. GET, PUT and
DELETE are; POST is not. Matters because clients retry.

**Status codes that matter here** — 201 Created (+ `Location`), 204 No Content,
400 malformed, 404 missing, 409 conflicts with current state, 500 unexpected.

---

## Architecture and design

**Layered architecture** — api → service → repository → domain, with dependencies
pointing inward only.

**DTO** (Data Transfer Object) — a shape designed for a boundary, separate from
the entity. Prevents mass assignment, decouples schema from API.

**Mass assignment** — a caller sets fields you never meant to expose by putting
them in the request body. A security vulnerability, not a style question.

**Command object** — the input to a use case, defined in the service layer so the
service never imports from the API layer. → `service/command/`

**Repository / DAO** — a collection-like facade over persistence. Purists
distinguish them; most codebases use the words interchangeably.

**Rich vs anemic domain model** — rules on the entity that owns the data, versus
entities that are pure getters and setters with all logic in services. The first
is preferred: a rule on the entity cannot be bypassed.

**Tell, don't ask** — put behaviour where the data lives instead of pulling data
out to decide elsewhere.

**Domain event** — a past-tense fact published by the domain.
`EnrollmentCreatedEvent`, never `CreateEnrollment`.

**Value object vs entity** — identity by value versus identity that persists
through change.

**Primitive obsession** — using `String` where a type belongs (`Email`).

**Static factory method** — a named constructor: `Email.of(...)`,
`Enrollment.create(...)`.

**Defensive copy** — returning an unmodifiable or copied collection so callers
cannot mutate your internals.

**Idempotent (operations)** — safe to run twice. Required of anything that starts
automatically or can be retried. → `config/DataSeeder.java`

**Fail fast** — refuse to start when misconfigured, rather than failing later at
runtime.

**Guard clause** — an early return or throw that handles the exceptional case
first, keeping the main path unindented.

---

## Validation

**Bean Validation** — the `jakarta.validation` constraint framework. You do not
call it; the container triggers it.

**Constraint / validator** — the annotation and the class implementing it.
→ `domain/validation/StudentNumber.java` + `StudentNumberValidator.java`

**Cascading validation** — `@Valid` on a nested object. Forget it and the nested
constraints silently never run.

**Validation groups** — validate a subset of constraints in a given situation
(`OnCreate` vs `OnUpdate`).

**Cross-field validation** — a rule spanning several fields; `@AssertTrue` on a
getter, or a class-level constraint. → `Enrollment.isHonoursConsistent()`

**The null rule** — a validator must treat `null` as valid. Presence is
`@NotNull`'s job.

---

## Build and operations

**GAV coordinates** — `groupId:artifactId:version`, an artifact's unique address.

**Maven scopes** — `compile` (default), `provided` (compile against it, do not
package — the server supplies it), `runtime`, `test`.

**WAR / JAR / EAR** — web archive, library, enterprise archive.

**Surefire vs Failsafe** — the Maven plugins that run `*Test` (unit, during
`mvn test`) and `*IT` (integration, during `mvn verify`).

**Deployment descriptor** — an XML configuration file: `persistence.xml`,
`beans.xml`, `web.xml`.

**JNDI** — the naming service the datasource is looked up through, so credentials
live in server configuration rather than in your code.

**Connection pool** — reused database connections. Sizing it larger than the
database can serve just moves the bottleneck.

**Correlation ID** — one identifier attached to every log line of a request, and
returned to the client. Turns log archaeology into one query.
→ `api/filter/CorrelationIdFilter.java`

**MDC** (Mapped Diagnostic Context) — the thread-local map SLF4J exposes to the
log layout as `%X{key}`. Must be cleared, because threads are pooled.

**Log injection / log forging** — an attacker puts newlines in a header that gets
logged, forging entries. Why inbound header values are sanitised.

**Schema migration** — versioned SQL applied in order (Flyway, Liquibase). The
production replacement for `hbm2ddl.auto`.

**Testcontainers** — starts a real database in Docker for a test run, so you test
against what you deploy against.

---

## Testing

**Unit test** — one class, all collaborators mocked. Milliseconds.
**Integration test** — several real components together.

**Mock / stub / spy** — a fake collaborator; a fake that returns canned answers;
a real object with some methods overridden.

**Arrange–Act–Assert** (also *Given–When–Then*) — the three sections every test
should visibly have.

**Test data builder / object mother** — a helper that constructs valid test
objects, so setup does not drown the assertion.

**Boundary testing** — test min−1, min, max, max+1. Off-by-one errors live at the
edges.

**Parameterized test** — one test method, many inputs, each reported separately.

**Fixed clock** — `Clock.fixed(...)`, so time-dependent behaviour is
deterministic and boundary instants are testable.

**Test isolation** — no test may depend on another's state or on execution order.
Achieved here by rolling back after each test.
