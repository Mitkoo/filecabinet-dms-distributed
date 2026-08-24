# main-app

The main FileCabinet application. Runs on port 8081.

## Responsibilities

- User registration and login with stateless JWT security
- Document library: upload, metadata, fields, delete, file download
- Review workflows: ordered reviewer steps, approve/reject, comments, cancel
- Categories and user role management (admin only)
- Calls the extraction service over Feign to extract invoice fields

## Tech

Spring Boot 3.4, Spring Security (JWT), Spring Data JPA + PostgreSQL,
Spring Cloud OpenFeign, Spring Data Redis (caching).

## Configuration

| Variable | Description |
|---|---|
| `DB_USERNAME` | database user (default `postgres`) |
| `DB_PASSWORD` | database password |
| `JWT_SECRET` | signing secret (has a development default) |
| `REDIS_HOST` / `REDIS_PORT` | Redis host/port (default `localhost:6379`) |
| `EXTRACTION_SERVICE_URL` | extraction-svc base URL (default `http://localhost:8082`) |

## Run

```
./mvnw -pl main-app spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile seeds sample data. Uses the `filecabinet_distr` database.

## REST API (main endpoints)

- `POST /api/auth/register`, `POST /api/auth/login`
- `GET/POST /api/documents`, `GET/PUT/DELETE /api/documents/{id}`
- `POST /api/documents/{id}/fields`, `POST /api/documents/{id}/mark-paid`
- `POST /api/workflows`, `GET /api/workflows/{id}`, `POST /api/workflows/{id}/steps/{stepId}/decision`
- `GET/POST /api/categories`, `GET /api/users`, `PUT /api/users/{id}/role`
- `GET/PUT /api/profile`
