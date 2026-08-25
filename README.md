# FileCabinet DMS (distributed)

A document management system for handling vendor invoices and other business documents.
It is split into two Spring Boot applications that talk to each other over Feign, plus a
React single-page frontend.

- **main-app** (port 8081) - documents, categories, users, review workflows, JWT security.
- **extraction-svc** (port 8082) - reads uploaded invoices and pulls out structured fields
  using an LLM (Mistral).
- **react-files** (port 5173) - the web UI (Vite + React + TypeScript).

## Functionality and workflow
Landing page with app info.
<img width="1162" height="1271" alt="image" src="https://github.com/user-attachments/assets/7b999a28-9011-4954-824a-32b6381b4e3e" />

Overview dashboard with your documents and the organization documents, inbox with approval request.
<img width="2490" height="656" alt="image" src="https://github.com/user-attachments/assets/45c95f21-f56d-4255-998d-fc5b18e0a516" />

Upload invoices and the Mistral based OCR extraction service will fill in the header fields and line items. 
<img width="2434" height="1266" alt="image" src="https://github.com/user-attachments/assets/b5a6b009-7df1-4637-9a15-973a7f6a720d" />

If the service catches issues it will mark the document for Clerk's human review.
<img width="2464" height="1261" alt="image" src="https://github.com/user-attachments/assets/ac3bcfe0-2768-4a98-87d3-7cbc079d0426" />

Users can review as human in the loop the quality of the extraction amend fields and start an approval chain to the Buyer, Manager and Accountant to pay the invoice.
<img width="2494" height="771" alt="image" src="https://github.com/user-attachments/assets/13887c39-0f94-4078-bcc0-16f17d08d0a4" />

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
