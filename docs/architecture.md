# Architecture

## System context

SprintForge is a stateless HTTP and STOMP service used by browser, mobile, or command-line clients. It owns credentials, projects, memberships, sprints, work items, attachment metadata, and audit history. PostgreSQL is required; Redis, SMTP, and S3 are independently configurable production integrations.

```mermaid
flowchart LR
    C[API clients] -->|HTTPS / JSON| API[Spring Boot API]
    C -->|Bearer JWT| API
    API -->|JDBC| DB[(PostgreSQL)]
    API -->|cache| R[(Redis)]
    API -->|presigned URLs| S3[(Amazon S3)]
    API -->|SMTP| M[Mail provider]
    API --> OBS[Prometheus / Grafana]
```

## Module boundaries

| Module | Responsibility | Owns |
| --- | --- | --- |
| `auth` | Registration, credential verification, JWT issuance and refresh rotation | Authentication endpoints, `refresh_tokens` |
| `user` | User identity and credential persistence | `app_users` |
| `project` | Project lifecycle, ownership, membership, access policy | `projects`, `project_members` |
| `sprint` | Sprint planning, lifecycle rules, operational counters | `sprints` |
| `task` | Assignment, guarded workflow, search, pagination, summaries | `work_items` |
| `audit` | Append-only records of significant state changes | `audit_events` |
| `attachment` | Attachment metadata and S3 presigned upload tickets | `attachments` |
| `notification` | Asynchronous project invitation delivery | SMTP integration |
| `realtime` | Project-scoped change events | STOMP topics |
| `config` | Security, OpenAPI, and datasource wiring | Application configuration |
| `common` | Consistent HTTP error responses | Shared error contract |

All modules run in one process and database. Capability boundaries keep dependencies visible and allow a module to be extracted later if scale or team ownership requires it.

## Request and security flow

```mermaid
sequenceDiagram
    participant Client
    participant Security as Spring Security
    participant API as Controller / Service
    participant DB as PostgreSQL

    Client->>Security: Request + Bearer JWT
    Security->>Security: Validate HS256 signature and expiry
    Security->>API: Authenticated user subject
    API->>DB: Load project through membership policy
    DB-->>API: Authorized aggregate or no result
    API->>DB: Commit state change + audit event
    API-->>Client: DTO + explicit HTTP status
```

Registration stores only a BCrypt hash. Login returns a short-lived JWT and an opaque refresh token. Only the refresh token's SHA-256 digest is persisted; every refresh rotates the token and revokes the previous value under a row lock. Spring Security maps the JWT role claim into the `ADMIN > MANAGER > USER` hierarchy before `ProjectAccessService` applies resource-level owner/member rules.

Project-scoped endpoints intentionally return `404 Not Found` when the caller is not a member, preventing identifier discovery. An existing member receives `403 Forbidden` when attempting an owner-only operation such as adding members or archiving the project.

## Data model

```mermaid
erDiagram
    APP_USERS {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar role
    }
    PROJECTS {
        uuid id PK
        uuid owner_id FK
        varchar project_key UK
        varchar status
        bigint version
    }
    PROJECT_MEMBERS {
        uuid project_id PK,FK
        uuid user_id PK,FK
    }
    SPRINTS {
        uuid id PK
        uuid project_id FK
        varchar status
        date start_date
        date end_date
        bigint version
    }
    WORK_ITEMS {
        uuid id PK
        uuid project_id FK
        uuid sprint_id FK
        uuid assignee_id FK
        varchar status
        varchar priority
        date due_date
        bigint version
    }
    AUDIT_EVENTS {
        uuid id PK
        uuid project_id FK
        uuid actor_id FK
        varchar action
        timestamptz occurred_at
    }
    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz expires_at
        timestamptz revoked_at
    }
    ATTACHMENTS {
        uuid id PK
        uuid project_id FK
        uuid work_item_id FK
        varchar object_key UK
        varchar status
    }

    APP_USERS ||--o{ PROJECTS : owns
    APP_USERS ||--o{ PROJECT_MEMBERS : joins
    PROJECTS ||--o{ PROJECT_MEMBERS : includes
    PROJECTS ||--o{ SPRINTS : plans
    PROJECTS ||--o{ WORK_ITEMS : contains
    SPRINTS o|--o{ WORK_ITEMS : groups
    APP_USERS o|--o{ WORK_ITEMS : assigned
    PROJECTS ||--o{ AUDIT_EVENTS : records
    APP_USERS ||--o{ AUDIT_EVENTS : performs
    APP_USERS ||--o{ REFRESH_TOKENS : owns
    PROJECTS ||--o{ ATTACHMENTS : stores
    WORK_ITEMS o|--o{ ATTACHMENTS : includes
```

- Application-generated UUIDs give entities stable identifiers before persistence.
- Foreign keys enforce ownership and project boundaries.
- Composite membership keys prevent duplicate memberships.
- Sprint dates have a database check constraint in addition to API validation.
- Indexes cover work-item status, assignee, sprint status, and audit timeline queries.
- `@Version` columns on projects, sprints, and work items detect conflicting writes.
- Partial indexes keep active project and work-item queries efficient while retaining soft-deleted records.

## Lifecycle rules

Work items follow explicit transitions:

```text
BACKLOG -> TODO -> IN_PROGRESS -> IN_REVIEW -> DONE
             ^          │             │         │
             └──────────┘             └─────────┘
```

The service also permits moving `TODO` back to `BACKLOG`. These rules are isolated in `WorkItemWorkflow` and unit tested. Sprints move from `PLANNED` to `ACTIVE`, then to `COMPLETED`; planned or active sprints may be cancelled. Invalid transitions return `409 Conflict`.

## Transactions and consistency

Mutating endpoints run inside database transactions. A domain change and its audit event commit or roll back together. Board-summary cache entries are evicted after mutations. Flyway owns schema evolution; Hibernate uses `validate` at startup and never creates or updates production tables. Assignment is accepted only when the assignee is already a project member, and a work item can reference only a sprint from the same project.

## Error contract

`ApiExceptionHandler` converts validation and domain failures into a stable JSON shape containing timestamp, status, error, message, path, and field errors where relevant. The API uses `400` for malformed input, `401` for authentication failures, `403` for known-but-forbidden actions, `404` for absent or intentionally hidden resources, and `409` for uniqueness and lifecycle conflicts.

## Operational model

- `/actuator/health` supports container and platform health checks.
- `/actuator/metrics` exposes JVM, HTTP, datasource, and custom application meters.
- `/actuator/prometheus` supplies scrape-ready monitoring data behind a constant-time metrics-key check.
- Sprint creation and status changes increment domain counters.
- Structured SLF4J events record significant actions using entity IDs rather than credentials or tokens.
- The application is stateless and can be replicated behind a load balancer.
- Runtime secrets and database credentials are injected through environment variables.
- Docker Compose provisions PostgreSQL, Redis, Prometheus, Grafana, and the API; the checked-in dashboard visualizes rate, errors, latency, heap usage, and domain counters.
- CI combines H2 integration tests with a Docker-aware PostgreSQL Testcontainers migration test, JaCoCo, SpotBugs, Trivy, OWASP Dependency-Check, and optional SonarCloud analysis.

## Design decisions and tradeoffs

### Modular monolith

A single deployable avoids distributed transactions and operational overhead at the current scale. Capability packages retain a clear path to service extraction.

### Symmetric JWT signing

HS256 keeps local and single-service deployment simple. A multi-service system should delegate identity to an OpenID Connect provider and use asymmetric keys with rotation.

### Repository-backed resource authorization

Authorization is resolved against relational ownership and membership data before project-scoped resources are returned. This limits accidental data exposure at the cost of coupling the current policy to the database model.

### Synchronous audit writes

Audit records share the state-change transaction and therefore cannot be silently lost after a successful response. If events need external consumers, a transactional outbox can preserve that guarantee while moving delivery off the request path.

### Layered integration tests

Most integration tests use H2 in PostgreSQL compatibility mode for fast feedback. A separate Docker-aware Testcontainers test boots PostgreSQL 17, executes every Flyway migration, validates the schema through Hibernate, and asserts the production-only tables. This keeps the common test loop fast without leaving migration compatibility untested.

### Optional external services

Redis is selected through Spring's cache abstraction, so Render can use the in-process cache while a multi-instance deployment selects Redis without code changes. SMTP delivery is asynchronous and disabled by default. S3 integration issues short-lived presigned PUT URLs; the API stores metadata but never proxies attachment bytes. Missing optional credentials return an explicit service-unavailable response instead of preventing application startup.
