# CLAUDE.md — TutorsPoint Backend

Non-negotiable engineering rules, derived from `TutorsPoint_Architecture.docx` and `TutorsPoint_PRD.docx`.
These are binding. Follow them without being re-told. If a request conflicts with a rule here, say so before writing code.

---

## 1. Stack

- Java **21**.
- Spring Boot **3.5.x**.
- **PostgreSQL** only. No other database, no H2 in production paths.
- **Maven** (`./mvnw`). Not Gradle.
- Base package: **`com.tutorspoint`**. Nothing outside it.

## 2. Architecture

Layered, single deployable modular monolith:

```
Controller  ->  Service  ->  Repository  ->  Domain
```

- Each layer depends only on the layer directly beneath it, through interfaces.
- **Controllers contain NO business logic.** Parse/validate input, delegate to a service, shape the HTTP response. Nothing else. A controller that makes a decision is a bug.
- **Repositories are Spring Data JPA interfaces** extending `JpaRepository`.
- **No SQL above the repository layer.** No native SQL, no JPQL, no `EntityManager`, no `Criteria` outside a repository. Complex queries drop to `@Query` JPQL *inside the repository interface* and no higher.
- No HTTP concern (servlet, request, response, status) below the controller.
- Transaction boundaries (`@Transactional`) live in the **service** layer.

## 3. Package structure — BY FEATURE, never by layer

There is no `controllers` package, no `services` package, no `dto` package at the root. Code is organised as vertical slices.

```
com.tutorspoint
├── tutor          // tutor accounts, TutorProfile, publish/unpublish
├── parent         // parent/student accounts, child sub-profiles, shortlists
├── search         // search criteria, filters, ranking strategies
├── enquiry        // on-platform enquiries + Request-a-Tutor
├── review         // ratings & reviews, verified-review rules
├── verification   // document review, verified-badge workflow
├── notification   // email / SMS channels (pluggable)
├── payment        // featured placement, gateway integrations
├── auth           // registration, login, JWT, role-based access
├── admin          // moderation, reference data, metrics
└── common         // BaseEntity, exceptions, config, response wrappers, utils
```

Every feature package holds **its own** controller, service (interface + impl), repository, entities, DTOs and mapper.
A new feature is a **new package**, not edits across five layer-packages.
Cross-feature reuse goes in `common` — never by reaching sideways into another feature's internals.

## 4. DTO boundary

- **Entities are NEVER returned from a controller, and never accepted as a request body.** No exceptions.
- Every endpoint takes a request DTO and returns a response DTO.
- Mapping lives in a **per-feature MapStruct mapper** (`TutorMapper`, `EnquiryMapper`, …). Conversion logic has exactly one home — never inline in a service or controller.
- DTOs carry exactly the fields that use case needs. Internal fields (verification notes, soft-delete flags, audit columns, password hashes) never leave the server.

## 5. Rich entities

- Fields are **private**. No public setters for state transitions.
- State changes go through **intention-revealing methods**: `tutor.publish()`, `tutor.verify()`, `enquiry.confirm()`, `review.flagAsVerified()` — not `setStatus(PUBLISHED)`.
- The entity enforces its own invariants; it does not trust callers.
- Lombok `@Setter` on an entity is a rule violation. `@Getter` is fine.
- `common.BaseEntity` owns `id` and audit fields. No entity redeclares them.

## 6. Services

- Every service is an **interface + implementation** (`TutorService` / `TutorServiceImpl`).
- Callers depend on the **interface**, never the impl (Dependency Inversion). Constructor injection via `@RequiredArgsConstructor`; no field injection, no `@Autowired` on fields.

## 7. External providers

Payment gateways, SMS providers, email, file storage, maps — all of them:

- Sit behind **our own interface** in our code (`PaymentGateway`, `NotificationChannel`, `FileStorage`).
- The **vendor SDK is confined to a single adapter class** (`PayHereGateway`, `WebXPaySmsAdapter`). No vendor type appears in a service signature, a DTO, an entity, or anywhere outside its adapter.
- Adding a provider = adding a class. Never editing existing, tested code.

## 8. Validation & errors

- Bean Validation annotations (`@NotNull`, `@Email`, `@Size`, `@Positive`) on **request DTOs**. `@Valid` on the controller parameter. Input is rejected at the edge, before business logic.
- Errors are handled **only** by the global `@RestControllerAdvice` in `common`, which maps domain exceptions to status codes and one standard error body.
- **No `try/catch` in controllers.** Domain code throws meaningful exceptions (`TutorNotFoundException`, `UnauthorizedActionException`); the advice formats them.

## 9. Database schema

- Schema changes happen **ONLY via Flyway migrations** in `src/main/resources/db/migration`, named `V<n>__<description>.sql`.
- **`spring.jpa.hibernate.ddl-auto=validate`. Never `update`, never `create`, never `create-drop`** — in any profile, including local dev.
- Migrations are forward-only and immutable once committed. Fix a mistake with a new migration.
- Reference data (Subject, Syllabus, Area) is normalised into its own tables — never free text in a search filter.

## 10. Configuration & secrets

- Database URL/credentials, JWT secret, gateway keys, SMS credentials: **environment variables only**.
- **Never hardcoded. Never committed.** `application.properties` holds `${ENV_VAR}` placeholders; real values live in a gitignored `.env` / the environment.
- Commit `application.properties.example` with placeholder values, never real ones.
- The same build artifact must be deployable to every environment.

## 11. Security

- Spring Security + **stateless JWT**. No server-side session state.
- Passwords hashed with **BCrypt**.
- Role-based authorization (`GUEST`, `PARENT`, `TUTOR`, `ADMIN`) enforced at route and method level. A tutor cannot touch another tutor's profile.
- Security is configured centrally in `common`/`auth` — never sprinkled through business code.
- Key actions (verification decisions, payments, moderation) are logged for audit.

## 12. Testing — mandatory, not optional

- **Every service class gets unit tests** with mocked dependencies (JUnit 5 + Mockito). No Spring context.
- **Every controller gets a slice test (`@WebMvcTest`) or an integration test.**
- A feature is not done until its tests exist and pass.

## 13. Localisation

- All user-facing text returned by the API that is translatable must be resolvable in **Sinhala, Tamil and English**.
- No hardcoded English strings in responses or error messages. Use message keys resolved through `MessageSource` against `messages_si.properties`, `messages_ta.properties`, `messages_en.properties`, honouring the request locale.
- This includes validation messages and global error-advice output.

## 14. Design patterns — only where they earn their place

Apply, do not decorate:

- **Strategy** — search ranking (`RankingStrategy`), payment gateway selection.
- **Repository** — Spring Data JPA.
- **Factory** — selecting the right `NotificationChannel` / `PaymentGateway`.
- **Observer** — domain events via `ApplicationEventPublisher` (enquiry confirmed → notify tutor, notify parent, update stats). The publisher does not know its listeners.
- **Adapter** — wrapping third-party SMS/payment APIs to our interfaces.
- **Builder** — complex `TutorProfile` / `Notification` construction.
- **DTO** — the API/persistence boundary.

Inheritance only for genuine "is-a" (`Tutor` is a `User`). Otherwise **composition**.
KISS/YAGNI: PostgreSQL queries + indexes for MVP search. No search engine, no caching layer, no abstraction until a requirement demands it.

---

## Checklist before finishing any change

- [ ] Code sits in the right **feature** package.
- [ ] Controller has zero business logic and zero `try/catch`.
- [ ] No entity crosses the controller boundary, in or out.
- [ ] Service is interface + impl; callers use the interface.
- [ ] Entity state changed via an intention-revealing method, not a setter.
- [ ] Request DTO carries Bean Validation annotations.
- [ ] Schema change has a Flyway migration; `ddl-auto` is still `validate`.
- [ ] No secret or environment-specific value is hardcoded or committed.
- [ ] Service unit tests + controller slice/integration tests written and passing.
- [ ] User-facing text is a message key resolvable in si / ta / en.
