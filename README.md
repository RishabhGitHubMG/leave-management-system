# Employee Leave Management System

A full-stack web app where employees register, apply for leave and track their requests, and admins approve or reject them.

**Stack:** React (Vite, React Router, Axios) · Spring Boot 4 (Java 17, Spring Web, Spring Data JPA, Spring Security) · MySQL 8 · JWT authentication

> 📖 **Architecture & Flow Guide**: See [SYSTEM_FLOW.md](./SYSTEM_FLOW.md) for sequence diagrams, end-to-end user workflows, security mechanisms, and codebase breakdown.

## Features

- **Public Landing Page**: High-conversion landing page with Settribe vector branding, interactive dashboard preview, feature benefits, step-by-step workflow, and enterprise security highlights.
- **Settribe Brand Design System**: Authentic logo vector, brand favicon, signature lime-to-yellow-green accent gradients, corporate navy typography, and seamless Light/Dark mode toggle.
- **Enterprise Dashboard**: Executive layout with 4 live KPI summary cards (Total Requests, Pending Approvals, Approved, Rejected), live keyword search, multi-filter toolbar (Status & Type), and sorting.
- **Interactive Modals**: Accessible `LeaveForm` modal with focus trapping, automatic duration calculation, and 500-char live counter; in-app `ConfirmModal` replacing native browser popups.
- **Advanced Validation & Conflict Defense**: Server-side overlapping date detection preventing duplicate active leaves, past-date bounds, RFC 5322 email regex, Name format constraints, and password strength evaluation.
- **Registration & Authentication**: BCrypt password hashing, Confirm Password verification, password visibility eye toggles, stateless HMAC-SHA256 JWT tokens.
- **Role-Based Workflows**: Dedicated employee portal (apply, edit, cancel pending) and administrative portal (view all company records, approve or reject).
- **Responsive & Accessible**: Mobile-first architecture tested from 320px mobile up to 4K displays, adhering to WCAG 2.2 AA standards.


## Project structure

```
backend/    Spring Boot API (controller -> service -> repository)
frontend/   React app
sql/        schema.sql (tables) and queries.sql (SQL assessment queries)
```

## Prerequisites

- JDK 17 or newer
- Node.js 18 or newer
- MySQL 8.0.16 or newer (earlier versions ignore CHECK constraints)

## Setup

### 1. Database

Create the schema:

```bash
mysql -u root -p < sql/schema.sql
```

Create a dedicated user for the app (replace the password):

```sql
CREATE USER 'leaveapp'@'localhost' IDENTIFIED BY 'choose_a_password';
GRANT ALL PRIVILEGES ON leave_management.* TO 'leaveapp'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Backend

Secrets are read from environment variables. Nothing sensitive is committed.

```bash
cd backend
export DB_USER=leaveapp
export DB_PASSWORD='choose_a_password'
export JWT_SECRET=$(openssl rand -base64 32)   # must be at least 32 characters
./mvnw spring-boot:run
```

The API runs on `http://localhost:8080`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. To point at a different API, set `VITE_API_URL` (default `http://localhost:8080/api`).

### 4. Create an admin

Registration always creates an `EMPLOYEE`, so nobody can self-assign admin. Register a user in the app, then promote them:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
```

## API

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register |
| POST | `/api/auth/login` | Public | Login, returns a JWT |
| GET | `/api/leaves?status=&type=` | Employee | List own requests (optional filters) |
| POST | `/api/leaves` | Employee | Apply for leave |
| PUT | `/api/leaves/{id}` | Owner | Update a pending request |
| DELETE | `/api/leaves/{id}` | Owner | Cancel (delete) a pending request |
| GET | `/api/admin/leaves?status=&type=` | Admin | List all requests |
| PUT | `/api/admin/leaves/{id}/status` | Admin | Approve or reject (`APPROVED` / `REJECTED`) |

Send the token as `Authorization: Bearer <token>`.

### Status codes

| Code | Meaning |
|---|---|
| 200 / 201 / 204 | Success, created, deleted |
| 400 | Validation error, invalid id, bad enum value |
| 401 | Missing, invalid or expired token; wrong credentials |
| 403 | Authenticated but not an admin |
| 404 | Not found, or the request belongs to another user |
| 409 | Duplicate email, or the request is no longer pending |

## Security and business rules

- Passwords are stored as BCrypt hashes.
- Every API except `/api/auth/**` requires a valid JWT; `/api/admin/**` requires the ADMIN role, enforced on the server.
- Users can only read, edit or cancel their own requests. Someone else's request returns 404, the same as a missing one, so ids cannot be probed.
- Only `PENDING` requests can be edited, cancelled, approved or rejected.
- `to_date` must not be before `from_date` (validated in the service and by a database CHECK constraint).
- Logout is client-side: the frontend deletes the token.
- Cancelling a request deletes the row, matching the "delete a pending request" SQL requirement.

## SQL

`sql/queries.sql` holds the assessment queries: list users, requests for a user, count by status, pending requests, approved requests by date, users joined with requests, update status and delete a pending request.

```bash
mysql -u leaveapp -p -t < sql/queries.sql
```
