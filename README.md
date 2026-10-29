# Employee Leave Management System

A full-stack web app where employees register, apply for leave and track their requests, and admins approve or reject them.

**Stack:** React (Vite, React Router, Axios) · Spring Boot 4 (Java 17, Spring Web, Spring Data JPA, Spring Security) · PostgreSQL 15+ with Flyway migrations · JWT authentication

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
backend/    Spring Boot API (controller -> service -> repository) and Flyway migrations
frontend/   React app
scripts/    PostgreSQL backup and restore-test helpers
sql/        Reference SQL: manual schema loader and assessment queries
```

## Prerequisites

- JDK 17 or newer (or use the bundled `./mvnw` wrapper)
- Node.js 18 or newer
- Docker with Compose v2 for the local PostgreSQL stack and the backup/restore helpers
- PostgreSQL 15 or newer for Docker and production. H2 is available only through the explicit `local` profile or tests.

## Setup

### 1. Local Docker PostgreSQL

The backend requires explicit database settings and never silently falls back to in-memory H2. For local development, opt into the `local` profile (section 2). For the Docker application, use the Compose `docker` profile and its persistent PostgreSQL 15 service. Create an ignored local environment file from the example and replace every placeholder with unique secrets:

```bash
cp .env.example .env
```

`.env` must define non-empty `POSTGRES_DB`, `DB_USER`, `DB_PASSWORD`, and `JWT_SECRET`. `DB_USER` becomes the PostgreSQL user that Compose creates; the official PostgreSQL image makes that user a superuser, which is acceptable only for this local stack. Do not reuse the same values anywhere else. Compose checks required values with `${NAME:?...}`; a defined-but-empty shell variable takes precedence over `.env` and produces an error naming the missing setting rather than silently using an empty credential. Unset any intentionally empty shell overrides before starting Compose.

Start the local database and API:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Stop and start without removing data:

```bash
docker compose stop
docker compose start
```

`docker compose down` removes containers and the network but keeps the named volume `postgres-data`. **Do not use `docker compose down -v`, `docker volume rm`, or volume-prune commands** if the database contains data.

The backend connects to `jdbc:postgresql://postgres:5432/<POSTGRES_DB>` on the Compose network. The `docker` profile runs Flyway (see [Flyway migrations](#flyway-migrations)) and then validates the JPA schema. The PostgreSQL port is published only on `127.0.0.1:${POSTGRES_HOST_PORT:-5432}`, and the API on `127.0.0.1:${API_HOST_PORT:-8080}`.

PostgreSQL initialization variables (`POSTGRES_DB`, `DB_USER`, `DB_PASSWORD`) apply only when the `postgres-data` volume is first created. Changing `DB_PASSWORD` later does not change an existing database's password. To rotate it, run `ALTER USER` inside the container with a new value, then update `.env` and restart the backend.

Check PostgreSQL health and startup logs:

```bash
docker compose ps
docker compose exec postgres sh -c 'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose logs --tail=100 backend
```

The Compose health check covers PostgreSQL only; the backend waits for it before starting.

#### Automatic backups to a separate disk

The `postgres-backup` service writes a custom-format `pg_dump` archive immediately after the PostgreSQL service is healthy and the three application tables exist, then repeats every `BACKUP_INTERVAL_SECONDS` (default 86400). It keeps the most recent `BACKUP_RETENTION_DAYS` window (default 30 days). Backups are written only to `BACKUP_DIR`, a host bind mount, not to the `postgres-data` volume. Backups contain password hashes and personal leave data.

Before starting Compose, mount a separate persistent disk (or a suitably mounted NAS) at the parent of `BACKUP_DIR`. Verify that the mount is present and physically separate from the Docker data disk. On Linux, inspect `findmnt -T "$(dirname "$BACKUP_DIR")"` and the Docker data-root mount. Do not create the marker until you have verified the mount, or an ordinary directory on the database disk could be mistaken for the backup destination.

Set `BACKUP_DIR` to that mounted directory and a unique random `BACKUP_VOLUME_MARKER` in `.env`. Create the marker file on the mounted disk using the same value:

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

The marker is bind-mounted read-only into the backup service. If it is absent or does not match, scheduled dumps fail closed. Monitor the backup service and disk space:

```bash
docker compose logs --tail=100 postgres-backup
docker compose exec postgres-backup df -h /backups
```

Each backup is a pair of files named `leave-management-<UTC timestamp>-<unique suffix>.dump` and the corresponding `.sha256` file. The script uses exclusive file creation so an existing backup cannot be overwritten, checks the custom-format archive with `pg_restore --list`, writes and verifies its SHA-256 checksum, then publishes the checksum without replacing an existing file. Retention deletes only old files matching `leave-management-*.dump` and their checksums in `BACKUP_DIR`; it never touches database records or volumes.

Use host-level disk encryption for the backup disk. A separate disk protects against failure of the database disk, but not theft, fire, or loss of the whole computer. Periodically copy encrypted backups to a separately administered off-site or object-storage destination and verify that copy. Protect the backup disk and `.env`.

Backups created by the former MySQL setup (`*.sql` files) are not managed by these scripts and cannot be restored into PostgreSQL. Keep them read-only until they have been migrated or intentionally retired.

#### Test a restore without overwriting data

Restore tests run inside the repository's local PostgreSQL 15 Compose container and always go to a **new** database name. The helper refuses remote Docker contexts, other Compose projects, `POSTGRES_DB`, and any name that already exists; it validates the checksum and archive before creating the test database, and never drops a database. A failed restore may leave its newly created test database partially populated; the helper still will not overwrite or remove it. Successful test databases are left for inspection.

```bash
backup_dir=$(sed -n 's/^BACKUP_DIR=//p' .env)
backup_file=$(find "$backup_dir" -maxdepth 1 -type f -name 'leave-management-*.dump' -print | sort | tail -n 1)
sh ./scripts/postgres-restore-test.sh "$backup_file" "restore_test_$(date +%s)"
```

The script prints the restored row counts for `users`, `leave_requests`, and `notifications`. Compare them with the source counts. Remove a test database only after you no longer need it, and only by its exact test name:

```bash
docker compose exec postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE restore_test_<timestamp>"'
```

To restore an archive manually into a new database, create the empty database first, then run `pg_restore --no-owner --no-privileges --exit-on-error -d <new_database> <file>.dump`. Never restore into the application database while the backend is running.

### 2. Backend outside Docker (optional)

Secrets are read from environment variables. Nothing sensitive is committed.

```bash
cd backend
export SPRING_PROFILES_ACTIVE=local
export DB_USER=sa
export DB_PASSWORD=
export DDL_AUTO=update
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

The explicit `local` profile uses in-memory H2 in PostgreSQL compatibility mode, and its data is lost when the backend stops. Flyway is disabled there, so `DDL_AUTO=update` lets Hibernate create the schema (the backend default is `validate`). To run against a real PostgreSQL database instead, set `SPRING_PROFILES_ACTIVE=docker` (or `render`), `DB_URL=jdbc:postgresql://...`, `DB_USER`, and `DB_PASSWORD`. The backend also requires `JWT_SECRET`; `ADMIN_EMAIL` and `ADMIN_PASSWORD` are optional bootstrap inputs and only create an account when none exists.

Tokens are HS256-signed with `JWT_SECRET`, use the user id as `sub`, and carry `iss` (`settribe-leave-management`), `iat`, `exp` (24 hours), `jti`, and `role`. To inspect a token locally, log in with `curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"<user>","password":"<password>"}'`, copy the `token` field, and paste it into https://jwt.io. Under signature verification, choose HS256 and enter the exact local `JWT_SECRET` as plain text, leaving "secret base64 encoded" unchecked. Use only local or test accounts and secrets on jwt.io, never production values.

The API runs on `http://localhost:8080`. The Docker Compose setup is local only; it is not connected to or automatically used by the online deployment.

### Running tests

Unit and web tests use in-memory H2 with the `test` profile:

```bash
cd backend
./mvnw test
```

The PostgreSQL migration tests are opt-in because they need a running PostgreSQL 15 database. They run Flyway V1 on that database, validate the Hibernate mapping, and check PostgreSQL constraints. Use a disposable database, never the application database:

```bash
cd backend
POSTGRES_MIGRATION_CI=true \
POSTGRES_TEST_URL=jdbc:postgresql://127.0.0.1:5432/leave_migration_test \
POSTGRES_TEST_USER=<test-user> \
POSTGRES_TEST_PASSWORD=<test-password> \
./mvnw test -Dtest=PostgresMigrationTests
```

The GitHub Actions workflow `.github/workflows/backend-postgres-migration.yml` runs the same check against a PostgreSQL 15 service container.

### Flyway migrations

- Location: `backend/src/main/resources/db/migration/`. The current baseline is `V1__initial_schema.sql`, which creates `users`, `leave_requests`, and `notifications` with their constraints and indexes.
- Flyway is enabled only by the `docker` and `render` profiles. It runs at startup before Hibernate validates the mapping. The `local` and `test` profiles do not use Flyway.
- Flyway records applied versions in its `flyway_schema_history` table. Never edit a migration that has been applied anywhere; Flyway reports a checksum mismatch. Add a new `V2__...sql` file for every schema change.
- Flyway initializes an **empty** database. It refuses a non-empty schema that has no Flyway history, so do not point the backend at an existing database that was not created by these migrations.
- `sql/schema.sql` includes this same V1 file for manual, throwaway databases. It is not a second schema source.

### Render Blueprint deployment

The root `render.yaml` defines one Docker web service that builds the React/Vite frontend and Spring Boot API together. The service targets `main` in Oregon and deploys on commits. The backend serves the SPA and `/api` routes on one origin, so no separate static-site URL, `VITE_API_URL`, or SPA rewrite is needed. The Blueprint references the existing Render Postgres resource `leave-management-db`; it does not declare or create a database resource.

The Blueprint sets `SPRING_PROFILES_ACTIVE=render`, generates `JWT_SECRET`, and resolves `DB_CONNECTION_STRING`, `DB_USER`, and `DB_PASSWORD` from the existing database. The application converts the private `postgresql://` connection string to a credential-free `jdbc:postgresql://HOST:PORT/DATABASE` `DB_URL` before validating and connecting. Render supplies `PORT` and `RENDER_EXTERNAL_URL`; the app uses the former and defaults same-origin CORS to the latter. If you attach a custom domain, set `CORS_ORIGIN` to that exact origin in Render.

The database resource must remain named `leave-management-db` and be in the same Render workspace as the Blueprint. Render updates referenced properties on Blueprint sync. Before first deployment, verify the database's backup and Flyway history; do not reset or baseline a populated database without a reviewed migration plan. No manual database credentials or `DB_URL` entry are required for the service. The Blueprint cannot verify database contents or perform deployment.

Render health checks use `GET /api/health`, which returns unavailable if the database cannot answer a simple readiness query. The `render` profile enables Flyway and fixes Hibernate to `validate`; do not set `DDL_AUTO`. `ADMIN_EMAIL` and `ADMIN_PASSWORD` are optional bootstrap settings that only create an account when the email does not already exist. `MAIL_USERNAME`, `MAIL_PASSWORD`, and `HR_EMAIL` are optional; email notifications require SMTP credentials, but these variables are not required for startup. The one-time `admin-account-update` profile is not used by the web service. After the initial setup, pushes to `main` trigger automatic deployments.

### Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `DB_URL is required` | The profile did not receive `DB_URL`. Set it in `.env` (Compose) or Render Environment. |
| `DB_URL must be a jdbc:postgresql URL` | A MySQL or other URL was set for `render`/`docker`. Use `jdbc:postgresql://host:5432/database`. |
| `DB_URL points to H2, which is not allowed on Render` | H2 is only for the `local` and test profiles. |
| `Connection refused` in Compose | Check `docker compose ps postgres` and `docker compose logs postgres`. Inside Compose the host is `postgres`, not `localhost`. |
| `password authentication failed` | `DB_PASSWORD` differs from the password stored in the volume. See the rotation note in section 1, or make sure Render's `DB_USER`/`DB_PASSWORD` match the database's credentials. |
| `Found non-empty schema(s) without schema history table` | The database already has tables not created by Flyway. Stop; do not add baselining without a reviewed plan. Use an empty database. |
| `Migration checksum mismatch` | An applied migration file was edited. Restore the original file and add a new `V<n>__...sql` migration instead. |
| `Schema-validation: missing table` or missing column | Flyway did not run (wrong profile) or the migration is incomplete. Confirm the active profile is `docker` or `render`. |
| `Backup destination is not the configured mounted backup disk` | The marker file is missing or differs from `BACKUP_VOLUME_MARKER`. Check the disk mount first. |
| Restore script refuses a name | The test name must be new and contain only letters, digits, and underscores, and cannot be `POSTGRES_DB`. |

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
- `to_date` must not be before `from_date`, validated in the service and enforced by the `chk_leave_dates` CHECK constraint in `backend/src/main/resources/db/migration/V1__initial_schema.sql`.
- Logout is client-side: the frontend deletes the token.
- Cancelling a request keeps the row and sets its status to `CANCELLED`, so history is preserved.

### Environment summary

| Setting | Local `local` profile | Docker Compose (`docker`) | Render (`render`) |
|---|---|---|---|
| Database | In-memory H2 (PostgreSQL mode), data lost on stop | PostgreSQL 15 container with a persistent volume | Managed PostgreSQL 15+ |
| `DB_URL` | `jdbc:h2:mem:...` (default in `backend/.env.example`) | `jdbc:postgresql://postgres:5432/<POSTGRES_DB>` (set by Compose) | `jdbc:postgresql://<host>:5432/<database>` |
| Flyway | Disabled | Enabled | Enabled |
| Hibernate schema mode | `DDL_AUTO=update` (set `DDL_AUTO` explicitly; default is `validate`) | `validate` | `validate` (fixed by profile) |
| `CORS_ORIGIN` | Defaults to `*` if unset | Defaults to `http://localhost:5173` in Compose | Defaults to Render's `RENDER_EXTERNAL_URL`; override for a custom domain |
| Backups | None | `postgres-backup` service to `BACKUP_DIR` | Provider backups plus your own verified export |

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The dev server proxies `/api` requests to `http://localhost:8080`, so `VITE_API_URL=/api` works locally. The frontend uses the relative `/api` path when `VITE_API_URL` is unset; the Vite proxy applies only in development. The Render Blueprint serves the frontend and API from one origin, so no production `VITE_API_URL` is required. For a separately hosted frontend, set `VITE_API_URL` at build time to the backend API base URL. Vite variables are included in browser-delivered assets and must never contain secrets.

## Create an admin

Registration always creates an `EMPLOYEE`, so nobody can self-assign a privileged role. For local Compose, set `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `.env` to seed an admin on first start. In Render, configure them as private service environment variables only if you intentionally want to seed a missing account. Existing administrator credentials are not reset. An existing admin can set other roles (`MANAGER`, `HR`, `ADMIN`) through `PUT /api/admin/users/{id}`.

## SQL reference files

- From the repository root, `psql -h 127.0.0.1 -U <user> -d leave_management_scratch -v ON_ERROR_STOP=1 -f sql/schema.sql` loads the Flyway V1 file into a manually created, empty PostgreSQL database. Run this command from the repository root; `\ir` resolves the included migration relative to `sql/schema.sql`. The script is not used by the application and must not be run against a database Flyway manages; Flyway remains the application's sole migration runner.
- `sql/queries.sql` contains assessment queries for PostgreSQL: `psql -h 127.0.0.1 -U <user> -d leave_management -f sql/queries.sql`. Statements 7 and 8 modify data.
