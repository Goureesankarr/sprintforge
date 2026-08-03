# SprintForge

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue) ![CI](https://github.com/sreedayags/sprintforge/actions/workflows/ci.yml/badge.svg)

A production-minded project management REST API built to demonstrate backend engineering practices beyond basic CRUD: secure authentication, project-level authorization, transactional workflows, database migrations, optimistic locking, audit trails, search, pagination, observability, documentation, testing, and containerized delivery.

## Highlights

- Stateless JWT authentication with BCrypt password hashing
- Project ownership and member-based access control
- Kanban work items with status, priority, assignment, due dates, search, filters, and pagination
- PostgreSQL schema managed by versioned Flyway migrations
- Optimistic locking to prevent silent concurrent-update loss
- Immutable audit events for important project and work-item actions
- Board summary endpoint for lightweight reporting
- Standardized RFC-style API errors and field-level validation details
- OpenAPI/Swagger UI, health checks, metrics, Docker Compose, and GitHub Actions CI

## Architecture

```text
HTTP / JSON
   │
Controllers + request validation
   │
Transactional domain workflows ─── JWT authorization
   │
Spring Data JPA repositories
   │
PostgreSQL + Flyway migrations
```

The code is organized by business capability (`auth`, `project`, `task`, `audit`, and `user`) so related HTTP, domain, and persistence code stays easy to navigate.

## Run locally

The fastest route starts both PostgreSQL and the API:

```bash
docker compose up --build
```

Then open:

- Swagger UI: `http://localhost:8080/docs`
- Health check: `http://localhost:8080/actuator/health`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Or start only PostgreSQL and run the application with Java 21:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

## API tour

Register and copy the returned token:

```bash
curl -s http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@example.com","password":"StrongPass123!","displayName":"Demo Developer"}'
```

Create a project:

```bash
curl -s http://localhost:8080/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Platform Launch","key":"PLAT","description":"Launch planning and delivery"}'
```

Create and query work items:

```bash
curl -s "http://localhost:8080/api/v1/projects/$PROJECT_ID/work-items" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Design database indexes","priority":"HIGH","status":"IN_PROGRESS"}'

curl -s "http://localhost:8080/api/v1/projects/$PROJECT_ID/work-items?status=IN_PROGRESS&priority=HIGH&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

## Quality checks

```bash
./mvnw clean verify
```

Integration tests use an isolated H2 database in PostgreSQL compatibility mode. CI runs the full Maven verification lifecycle on every pull request and push to `main`.

## Resume-ready description

**SprintForge — Project Management REST API**
Built a secure, containerized project-management backend with Java 21, Spring Boot, Spring Security, JPA, PostgreSQL, and Flyway. Implemented JWT authentication, project-level authorization, paginated work-item search, optimistic concurrency control, audit logging, OpenAPI documentation, integration tests, health/metrics endpoints, and GitHub Actions CI.

## Tech stack

Java 21 · Spring Boot 4 · Spring Security · Spring Data JPA · PostgreSQL · Flyway · OpenAPI · Maven · JUnit · Docker · GitHub Actions

## License

MIT
