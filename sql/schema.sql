-- PostgreSQL 15+ manual schema loader for a FRESH, EMPTY database only.
--
-- Authoritative schema: backend/src/main/resources/db/migration/V1__initial_schema.sql
-- (applied automatically by Flyway in the docker and render profiles). This file does not
-- duplicate those definitions; it includes the Flyway script so there is one schema source.
--
-- Do not run this against Render or any database Flyway already manages: Flyway would not
-- see its schema history and the CREATE TABLE statements would fail or conflict.
--
-- Usage from the repository root, after creating an empty database:
--   createdb -h 127.0.0.1 -U <user> leave_management_scratch
--   psql -h 127.0.0.1 -U <user> -d leave_management_scratch -v ON_ERROR_STOP=1 -f sql/schema.sql

\set ON_ERROR_STOP on
\i backend/src/main/resources/db/migration/V1__initial_schema.sql
