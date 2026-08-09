# FileCabinet DMS

Individual project for the Spring Fundamentals course. It is a simple document management system where users can upload documents (invoices, contracts, receipts, legal filings), organize them into categories, add extracted data fields to them, and send them through a review/approval workflow before they are marked as paid.

<img width="2482" height="1064" alt="image" src="https://github.com/user-attachments/assets/ea7bc6ea-d118-4923-a049-4a7cc8409d65" />

## Tech stack

- Java 17
- Spring Boot 3.4.0 (Spring MVC, Spring Data JPA, Spring Validation)
- Thymeleaf + Bootstrap 5 for the frontend
- PostgreSQL database
- Maven
- Lombok
- BCrypt (from spring-security-crypto) for password hashing

## Entities

- User - account used to log in (not a domain entity, just for auth)
- Category - a folder/label to organize documents
- Document - the main entity, an uploaded file with a type and a status
- DocumentField - a key/value field attached to a document (e.g. InvoiceNumber = INV-001)
- ReviewWorkflow - a review process started on a document
- ReviewStep - one reviewer's step inside a workflow
- WorkflowEvent - a log entry for what happened in a workflow (decision, comment, reminder)
- WorkflowRead - keeps track of which workflows a user has already seen/read


<img width="2502" height="984" alt="image" src="https://github.com/user-attachments/assets/9aeb8b4e-d8b9-48d8-a4ec-ee4de2a82154" />

## Roles

- CLERK - default role, can upload/edit/delete their own documents and send them for review
- MANAGER, BUYER, ACCOUNTANT - different reviewer roles used in the invoice approval pipeline
- ADMIN - can manage everything, approve/reject any document, manage users


  <img width="2498" height="764" alt="image" src="https://github.com/user-attachments/assets/76f26b25-f4c8-4cf3-bc53-cfd4687589c8" />

## Main features / functionalities

- Register / login / logout (session based, user id stored in session)
- Upload a document, edit its details, delete it, change its status (full CRUD on Document)
- Add and remove data fields on a document
- Start a review workflow on a document and pick the reviewers
- Reviewers approve or reject their step, in order
- Send a reminder to the current reviewer (with a cooldown so it can't be spammed)
- Cancel a workflow that is in progress
- Comment on a workflow
- Mark an approved invoice as paid
- Admin can view/manage all users and their roles
- Edit your own profile / reset your password if you forget it
- Rich embedded invoice documents flow, with full approval chain per role and final payment functionality from the ACCOUNTANT role.

  <img width="2480" height="1275" alt="image" src="https://github.com/user-attachments/assets/7b5134e0-c415-4de3-805b-fc18659a52e8" />

## Security

- Login is session based, no Spring Security filter chain, just a `SessionAuthInterceptor` that checks if there is a logged in user id in the session for protected pages.
- Guests can only see the landing page, register, login and forgot password pages.
- Logged in users can access documents, workflows and their profile.
- Some actions are also role restricted, for example only an admin or accountant can mark a document as paid, and only an admin can manage users.

## How to run it

1. Have PostgreSQL running locally and create a database called `filecabinet`.
2. Set the `DB_USERNAME` and `DB_PASSWORD` environment variables (or edit `application.properties` directly).
3. Run `mvnw spring-boot:run`.
4. App will be available on `http://localhost:8081`.

On first run, some sample data (users, categories, documents) is generated automatically so the app is not empty.

You can use the DEMO account to view the app. Also, you can create and reset the password of account easily by just providing the user name, email and the new password, then it is set. It is useful to have all the types of demo accounts CLERK, BUYER, MANAGER and ACCOUNTANT to try the whole rich invoice processing document flow.

## Future improvements

- Actual OCR instead of manually typed fields
- Exporting approved documents to an external system (ERP)
- File versioning
- Add legal documents and contracts support and such workflows
- Add digital signatures and other integrations
- Add archiving service and possibility to move that backup archive automatically on on-prem file system or file server
