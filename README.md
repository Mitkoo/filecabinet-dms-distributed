# FileCabinet DMS (distributed)

A document management system for handling vendor invoices and other business documents.
It is split into two Spring Boot applications that talk to each other over Feign, plus a
React single-page frontend.

- **main-app** (port 8081) - documents, categories, users, review workflows, JWT security.
- **extraction-svc** (port 8082) - reads uploaded invoices and pulls out structured fields
  using an LLM (Mistral).
- **react-files** (port 5173) - the web UI (Vite + React + TypeScript).

## Architecture

```
  React SPA (5173)
       |  REST + JWT
       v
   main-app (8081)  --- Feign (JWT) --->  extraction-svc (8082)
       |                                       |
  Postgres (filecabinet_distr)          Postgres (filecabinet_extraction)
       |
     Redis (cache)
```

When an invoice is uploaded to main-app it stores the file and calls extraction-svc over
Feign to queue an extraction job. extraction-svc processes the queue on a schedule, calls
Mistral, and stores the extracted fields. main-app reads the result back through the same
Feign client.

## Tech stack

- Java 17, Spring Boot 3.4
- Spring Security with stateless JWT (jjwt)
- Spring Data JPA + PostgreSQL
- Spring Cloud OpenFeign
- Spring Data Redis (caching)
- Spring AI (Mistral) + Apache PDFBox
- React 19 + TypeScript + Vite + Tailwind CSS
- JUnit 5, Mockito, JaCoCo

## Requirements

- Java 17
- Node.js 20+
- PostgreSQL 16+
- Redis (or Docker)

## Running locally

Create the two databases:

```
createdb filecabinet_distr
createdb filecabinet_extraction
```

Set the environment variables the apps need:

- `DB_PASSWORD` - PostgreSQL password
- `MISTRAL_API_KEY` - Mistral API key (extraction-svc)
- `JWT_SECRET` - optional, has a development default

Start the backends (from the repository root):

```
./mvnw -pl extraction-svc spring-boot:run
./mvnw -pl main-app spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile seeds sample users and documents. Default login: `jane.doe` / `password123`.

Start the frontend:

```
cd react-files
npm install
npm run dev
```

Open http://localhost:5173. The Vite dev server proxies `/api` to the main app.

## Running with Docker Compose

```
export MISTRAL_API_KEY=your-key
docker compose up --build
```

This starts both applications, two PostgreSQL instances and Redis.

## Functionalities

Main app:

- Register / log in (JWT)
- Upload a document (invoices are sent to the extraction service automatically)
- Update document metadata, add and remove fields, delete a document
- Start a review workflow, approve or reject a step, comment, cancel
- Mark an approved invoice as paid
- Create categories (admin), manage user roles (admin)

Microservice (called through Feign):

- Queue an extraction when a document is uploaded
- Reprocess an extraction
- Delete the extraction job when its document is deleted

## Testing

```
./mvnw test
```

JaCoCo coverage reports are written to `*/target/site/jacoco/index.html`.
