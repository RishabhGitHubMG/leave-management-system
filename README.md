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
- MySQL 8.0.16 or newer for Docker and production; H2 is available only through the explicit local profile or tests

## Setup

### 1. Local Docker MySQL

The backend requires explicit database settings and never silently falls back to in-memory H2. For local development, opt into the `local` profile; for the Docker application, use the Compose `docker` profile and its persistent MySQL 8 service. Create an ignored local environment file from the example and replace every placeholder with unique secrets:

```bash
cp .env.example .env
```

Start the local database and API:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

The Docker profile connects to the Compose service using `DB_URL`, `DB_USER`, and `DB_PASSWORD`, runs the checked-in Flyway migration on an empty database, and then validates the JPA schema. Other profiles keep Flyway disabled and retain the existing `DDL_AUTO` default. MySQL database files live in the project-scoped named volume whose Compose key is `mysql-data`; stopping or recreating containers does not remove it. `restart: unless-stopped` restarts services when Docker starts, provided the Docker engine itself is configured to start after a computer reboot. MySQL initialization variables are applied only when the volume is first created; changing `.env` later does not rotate an existing MySQL user's password.

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

#### Automatic backups to a separate disk

The `mysql-backup` service makes a consistent SQL dump immediately after the MySQL schema is ready, then repeats every `BACKUP_INTERVAL_SECONDS` (default 86400). It retains the most recent `BACKUP_RETENTION_DAYS` window (default 30 days). The backup service writes only to `BACKUP_DIR`, a host bind mount—not to the MySQL named volume. Backups contain private account and leave data.

Before starting Compose, mount a separate persistent disk (or a suitably mounted NAS) at the parent of `BACKUP_DIR`. Verify that mount is present and physically separate from the Docker data disk; for Linux hosts, inspect `findmnt -T "$(dirname "$BACKUP_DIR")"` and the Docker data-root mount. Do not create the marker until you have verified the mount, or an ordinary directory on the database disk could be mistaken for the backup destination.

Set `BACKUP_DIR` to that mounted directory and set a unique random `BACKUP_VOLUME_MARKER` in `.env`. Create the marker file on the mounted disk using the same value:

```bash
backup_dir=$(sed -n 's/^BACKUP_DIR=//p' .env)
backup_marker=$(sed -n 's/^BACKUP_VOLUME_MARKER=//p' .env)
findmnt -T "$(dirname "$backup_dir")"
sudo mkdir -p "$backup_dir"
findmnt -T "$(docker info --format '{{.DockerRootDir}}')"
printf '%s\n' "$backup_marker" | sudo tee "$backup_dir/.backup-volume-marker" >/dev/null
sudo chmod 600 "$backup_dir/.backup-volume-marker"
docker compose up --build -d
```

The marker is bind-mounted read-only into the backup service. If the marker is absent or does not match, scheduled dumps fail closed. Monitor the backup service and disk space:

```bash
docker compose logs --tail=100 mysql-backup
docker compose exec mysql-backup df -h /backups
```

Use host-level disk encryption for the separate backup disk. A separate disk protects against failure of the database disk, but not theft, fire, or loss of the whole computer. Periodically copy encrypted backups to a separately administered off-site/object-storage destination and verify that copy. Protect the MySQL backup disk and `.env`; the dump contains password hashes and personal leave data. The retention job deletes only old matching backup files in the configured backup directory, never database records or volumes.

#### Test a restore without overwriting data

Choose a unique, unused test-database name. The helper refuses to use the configured application database or any existing schema, creates a separate empty schema, imports the dump, and reports row counts. It deliberately leaves the restored test database intact for inspection; it never drops a database.

```bash
backup_dir=$(sed -n 's/^BACKUP_DIR=//p' .env)
backup_file=$(find "$backup_dir" -maxdepth 1 -type f -name 'leave-management-*.sql' -print | sort | tail -n 1)
sh ./scripts/mysql-restore-test.sh "$backup_file" "restore_test_$(date +%s)"
```

Record the reported counts and compare them with the source counts. Before testing persistence, create clearly identified test users, a manager relationship, leave requests in pending and approved/rejected states, and notifications in the local app; record IDs and counts. Then run `docker compose restart backend`, `docker compose restart mysql`, and `docker compose up --force-recreate -d mysql backend mysql-backup`—never add `-v`—and query those same IDs/counts again. These live restart/restore checks must be performed on a machine with Docker running; they have not been verified in this development environment.

### 2. Backend outside Docker (optional)

Secrets are read from environment variables. Nothing sensitive is committed.

```bash
cd backend
export SPRING_PROFILES_ACTIVE=local
export DB_USER=sa
export DB_PASSWORD=
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

The explicit `local` profile uses in-memory H2, and its data is lost when the backend stops. For a separately managed MySQL database, set `DB_URL`, `DB_USER`, and `DB_PASSWORD` in the environment and use the `render` or `docker` profile as appropriate. The backend also requires `JWT_SECRET`; `ADMIN_EMAIL` and `ADMIN_PASSWORD` are optional bootstrap inputs and only create an account when none exists.

The API runs on `http://localhost:8080`. The Docker Compose setup is local only; it is not connected to or automatically used by the online deployment. Production uses the explicit `render` profile and requires a persistent database, stable strong `JWT_SECRET`, and frontend origin in `CORS_ORIGIN`; its schema mode is `validate`. No deployment is performed by this repository change.

### Render production configuration (manual, not performed here)

For the online service, configure a persistent MySQL 8 database (Render persistent-disk MySQL service or another managed MySQL provider) and connect over its private/internal hostname when available. Obtain its database name, host, port, username, and password from that provider's dashboard. Set these Render backend environment variables:

- `SPRING_PROFILES_ACTIVE=render`
- `DB_URL=jdbc:mysql://<private-host>:<port>/<database>?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`
- `DB_USER=<database-user>`
- `DB_PASSWORD=<database-password>`
- `JWT_SECRET=<unique random secret of at least 32 bytes>`
- `CORS_ORIGIN=<exact frontend origin>`
- `DDL_AUTO=validate`

Enter secret values only in the Render Environment settings, never in Git, a Docker image, build arguments, or chat. Leave `ADMIN_EMAIL` and `ADMIN_PASSWORD` unset unless intentionally creating a missing bootstrap account; they do not update an existing account.

Before changing a running service, identify its actual current database and verify a restorable backup. If it has important H2 memory data, export it before any restart or redeploy; it disappears when that process stops. Only after a reviewed backup should an empty new MySQL database be provisioned with `utf8mb4` / `utf8mb4_unicode_ci` and initialized from `backend/src/main/resources/db/migration/V1__initial_schema.sql`. Do not run that fresh-database migration on a non-empty or unidentified schema. The Render profile keeps Flyway disabled and Hibernate at `validate`; it will not create, update, baseline, or reset the schema. Deploy manually only after the database and schema are ready; then inspect Render logs for the Render profile and successful MySQL connection, and verify `DB_URL` does not use `jdbc:h2:mem:`. Local Compose backups do not back up Render data; configure the production database provider's backups plus a verified encrypted off-site export before migration or deployment. This guide does not connect to or modify Render.

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

