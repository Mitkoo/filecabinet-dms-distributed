# extraction-svc

The invoice extraction microservice. Runs on port 8082 with its own database.

## Responsibilities

- Accept extraction jobs from the main app
- Read the PDF text layer (Apache PDFBox) and extract structured invoice fields with Mistral
- Process queued jobs on a schedule and store the extracted fields
- Expose the job status and fields back to the main app

## Tech

Spring Boot 3.4, Spring Data JPA + PostgreSQL, Spring AI (Mistral), Apache PDFBox,
Spring scheduling.

## Configuration

| Variable | Description |
|---|---|
| `DB_USERNAME` | database user (default `postgres`) |
| `DB_PASSWORD` | database password |
| `MISTRAL_API_KEY` | Mistral API key |
| `JWT_SECRET` | signing secret shared with main-app, used to authenticate incoming requests (has a development default; set a real value in production) |

Uses the `filecabinet_extraction` database.

## Run

```
./mvnw -pl extraction-svc spring-boot:run
```

## REST API

Every endpoint below (except `/actuator/**`) requires a valid `Authorization: Bearer <token>` header, signed with `JWT_SECRET`. main-app relays the caller's token automatically.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/extractions` | queue a job for an uploaded document |
| `PUT` | `/api/extractions/{id}/reprocess` | reset and re-run a job |
| `DELETE` | `/api/extractions/{id}` | remove a job |
| `GET` | `/api/extractions/by-document/{documentId}` | poll status and extracted fields |

## Scheduling

- Fixed-delay job (every 10s) drains the queue, runs extraction and stores the fields.
- Nightly cron (02:00) purges old completed jobs and requeues stuck ones.
