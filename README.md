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
```

## Prerequisites

- JDK 17 or newer
- Node.js 18 or newer
- MySQL 8.0.16 or newer (optional; the backend uses an embedded H2 database by default)

## Setup

### 1. Local Docker MySQL

The default backend configuration still uses in-memory H2. For a persistent local MySQL 8 database and backend, create an ignored local environment file from the example and replace every placeholder with unique secrets:

```bash
cp .env.example .env
```

Start the local database and API:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

The Docker profile connects to the Compose service using `DB_URL`, `DB_USER`, and `DB_PASSWORD`, runs the checked-in Flyway migration on an empty database, and then validates the JPA schema. Other profiles keep Flyway disabled and retain the existing `DDL_AUTO` default. The MySQL database files live in the named `mysql-data` volume; stopping or recreating containers does not remove it. MySQL's initialization variables are applied only when that volume is first created; changing `.env` later does not rotate an existing MySQL user's password.

Stop and restart without removing data:

```bash
docker compose stop
docker compose start
```

`docker compose down` removes containers and network but keeps the named volume. **Do not use `docker compose down -v`, `docker volume rm`, or volume-prune commands** if the database contains data.

Check MySQL health and the backend startup logs:

```bash
docker compose ps
docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping --silent -h 127.0.0.1 -uroot'
docker compose logs --tail=100 backend
```

The application does not currently expose an HTTP health endpoint; the Compose health check covers MySQL only.

Back up the database to a local file and restore it into a deliberately selected target database:

```bash
umask 077
mkdir -p backups
backup_file="backups/leave-management-$(date +%F-%H%M%S).sql"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --skip-add-drop-table "$MYSQL_DATABASE"' > "$backup_file"

# Choose a unique, empty restore database name; do not restore over a database with data.
restore_db="leave_management_restore_$(date +%s)"
docker compose exec -e RESTORE_DATABASE="$restore_db" mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "CREATE DATABASE $RESTORE_DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"'
docker compose exec -T -e RESTORE_DATABASE="$restore_db" mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$RESTORE_DATABASE"' < "$backup_file"
```

Keep backup files private; they contain account and leave data. The restore example creates a separate database and intentionally refuses to drop tables. Confirm that the chosen target name is unused before restoring.

### 2. Backend outside Docker (optional)

Secrets are read from environment variables. Nothing sensitive is committed.

```bash
cd backend
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

This mode uses in-memory H2 by default, and its data is lost when the backend stops. For a separately managed MySQL database, set `DB_URL`, `DB_USER`, and `DB_PASSWORD` in the environment. The backend also requires `JWT_SECRET`; `ADMIN_EMAIL` and `ADMIN_PASSWORD` are optional bootstrap inputs and only create an account when none exists.

The API runs on `http://localhost:8080`. For deployment, configure a persistent database, a stable strong `JWT_SECRET`, and the frontend origin in `CORS_ORIGIN`. The Docker Compose setup in this repository is for local development only; it is not connected to or automatically used by the online deployment. `DDL_AUTO` defaults to `update` outside the Docker profile.

### Existing data and migration

The repository does not identify the database currently used by the online deployment, and this setup does not inspect, migrate, or replace it. The default H2 database is in-memory, while an externally configured database may persist data elsewhere. Before any data migration, identify the active source, take and verify a backup, restore it into a separate staging MySQL database, then compare user/leave/notification IDs, row counts, foreign keys, and authentication behavior. Do not point the Compose backend at an existing non-empty database: its Flyway migration is for a fresh database, not an existing-schema upgrade.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` requests to `http://localhost:8080`, so `VITE_API_URL=/api` works locally. If `VITE_API_URL` is unset, the app uses the Render backend URL when the page hostname contains `onrender.com`; otherwise it calls `http://localhost:8080/api`. Vite variables are baked into the frontend at build time. For a separately hosted frontend and backend, set `VITE_API_URL` to the backend API base URL or configure a production `/api` reverse proxy; the Vite dev proxy is not used in production.

### 4. Create an admin

Registration always creates an `EMPLOYEE`, so nobody can self-assign a privileged role. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` (step 2) to seed an admin on first start. An existing admin can set other roles (`MANAGER`, `HR`, `ADMIN`) through `PUT /api/admin/users/{id}`.

## API

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register |
| POST | `/api/auth/login` | Public | Login, returns a JWT |
| GET | `/api/users/me` | Authenticated | Read own profile |
| PUT | `/api/users/me` | Authenticated | Update own profile |
| GET | `/api/leaves?status=&type=` | Authenticated | List own requests (optional filters) |
| POST | `/api/leaves` | Authenticated | Apply for leave |
| PUT | `/api/leaves/{id}` | Owner | Update a pending request |
| DELETE | `/api/leaves/{id}` | Owner | Cancel a pending request (kept with status `CANCELLED`) |
| GET | `/api/notifications` | Authenticated | List own notifications |
| GET | `/api/notifications/unread-count` | Authenticated | Get unread notification count |
| PUT | `/api/notifications/{id}/read` | Authenticated | Mark own notification as read |
| PUT | `/api/notifications/read-all` | Authenticated | Mark all own notifications as read |
| GET | `/api/admin/leaves?status=&type=` | Manager, HR, Admin | List requests the caller may review (managers see direct reports only) |
| PUT | `/api/admin/leaves/{id}/status` | Manager (direct reports), HR, Admin | Approve or reject (`APPROVED` / `REJECTED`) |
| GET | `/api/admin/users` | Admin | List users |
| POST | `/api/admin/users` | Admin | Create a user |
| PUT | `/api/admin/users/{id}` | Admin | Update user details, role, or active status |

Send the token as `Authorization: Bearer <token>`.

### Status codes

| Code | Meaning |
|---|---|
| 200 / 201 / 204 | Success, created, deleted |
| 400 | Validation error, invalid id, bad enum value |
| 401 | Missing, invalid or expired token; wrong credentials |
| 403 | Authenticated but not authorized for the requested admin operation |
| 404 | Not found, or the request belongs to another user |
| 409 | Duplicate email, or the request is no longer pending |

## Security and business rules

- Passwords are stored as BCrypt hashes.
- Every API except `/api/auth/**` requires a valid JWT. `/api/leaves/**`, `/api/users/me`, and `/api/notifications/**` require authentication but do not impose a role restriction; personal leave operations are scoped to the authenticated user. `/api/admin/leaves/**` is available to MANAGER, HR and ADMIN (managers are scoped to direct reports); other `/api/admin/**` routes require ADMIN. Enforced on the server.
- Users can only read, edit or cancel their own requests. Someone else's request returns 404, the same as a missing one, so ids cannot be probed.
- Only `PENDING` requests can be edited, cancelled, approved or rejected.
- `to_date` must not be before `from_date` (validated in the service and enforced by the `chk_leave_dates` CHECK constraint in `sql/schema.sql`). The SQL script is for fresh database setup; no migration script for existing databases is included.
- Logout is client-side: the frontend deletes the token.
- Cancelling a request keeps the row and sets its status to `CANCELLED`, so history is preserved.

