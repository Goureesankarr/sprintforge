# SprintForge

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue) ![CI](https://github.com/Goureesankarr/sprintforge/actions/workflows/ci.yml/badge.svg)

SprintForge is a REST API for managing projects and Kanban-style work items. A project owner can invite registered users, members can coordinate work through a shared board, and significant changes are recorded in an audit trail.

## Features

- Email/password registration and stateless JWT authentication
- Project ownership and member-based authorization
- Work-item assignment, priorities, due dates, and Kanban statuses
- Text search, filtering, sorting, and pagination
- Per-project board summaries
- Optimistic locking for concurrent updates
- Audit events for project and work-item changes
- Flyway-managed PostgreSQL schema
- OpenAPI documentation and operational health endpoints

## Architecture

SprintForge is a modular monolith organized around business capabilities:

```text
Client
  │ HTTPS + JWT
  ▼
REST controllers ── validation and access checks
  │
  ├── auth       registration and token issuance
  ├── project    ownership and membership
  ├── task       work-item lifecycle and board queries
  ├── audit      append-only change history
  └── user       identities and persistence
  │
Spring Data JPA
  │
PostgreSQL ── schema versioned by Flyway
```

Requests are authenticated by Spring Security before reaching the controllers. Project-scoped operations verify membership in the repository layer and return `404` for inaccessible resources, avoiding disclosure of project identifiers. State changes and their audit events share a transaction.

See [Architecture](docs/architecture.md) for the component boundaries, data model, security flow, and design decisions.

## Running locally

### Docker Compose

```bash
docker compose up --build
```

### Maven

Start PostgreSQL, then run the service with Java 21:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Local endpoints:

- Swagger UI: `http://localhost:8080/docs`
- OpenAPI document: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

Configuration is supplied through environment variables. Copy `.env.example` when setting up a local environment and use a randomly generated `JWT_SECRET` outside local development.

## Example workflow

Register a user:

```bash
curl -s http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alex@example.org","password":"StrongPass123!","displayName":"Alex Morgan"}'
```

Use the returned token to create a project:

```bash
curl -s http://localhost:8080/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Platform Launch","key":"PLAT","description":"Launch planning and delivery"}'
```

Create and query work items:

```bash
curl -s "http://localhost:8080/api/v1/projects/$PROJECT_ID/work-items" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Review database indexes","priority":"HIGH","status":"IN_PROGRESS"}'

curl -s "http://localhost:8080/api/v1/projects/$PROJECT_ID/work-items?status=IN_PROGRESS&priority=HIGH&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

## Tests

```bash
./mvnw clean verify
```

Integration tests cover registration, validation, authenticated project creation, work-item workflows, board summaries, and cross-user authorization. CI runs the Maven verification lifecycle for pushes and pull requests.

## Technology

Java 21 · Spring Boot 4 · Spring Security · Spring Data JPA · PostgreSQL · Flyway · OpenAPI · Maven · JUnit · Docker · GitHub Actions

## License

Licensed under the [MIT License](LICENSE).
