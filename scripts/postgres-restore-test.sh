#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "Usage: scripts/postgres-restore-test.sh BACKUP_FILE NEW_TEST_DATABASE" >&2
  exit 2
fi

backup_file=$1
restore_database=$2
case "$backup_file" in
  -*) backup_file=./$backup_file ;;
esac
backup_name=$(basename "$backup_file")
backup_directory=$(dirname "$backup_file")
checksum_file="${backup_file}.sha256"
checksum_name=$(basename "$checksum_file")
if [ ! -s "$backup_file" ] || [ ! -s "$checksum_file" ]; then
  echo "Backup file or checksum is missing/empty" >&2
  exit 2
fi
case "$backup_name" in
  leave-management-*.dump) ;;
  *) echo "Backup filename is not a managed PostgreSQL archive" >&2; exit 2 ;;
esac
if [ "$(wc -l < "$checksum_file" | tr -d '[:space:]')" != 1 ]; then
  echo "Checksum file must contain exactly one entry" >&2
  exit 2
fi
IFS=' ' read -r checksum_hash checked_name extra < "$checksum_file"
case "$checksum_hash" in
  ''|*[!0-9a-fA-F]*) echo "Checksum file has an invalid SHA-256 digest" >&2; exit 2 ;;
esac
if [ "${#checksum_hash}" -ne 64 ] || [ "$checked_name" != "$backup_name" ] || [ -n "${extra:-}" ]; then
  echo "Checksum file does not match the selected backup" >&2
  exit 2
fi
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$backup_directory" && sha256sum -c "$checksum_name")
elif command -v shasum >/dev/null 2>&1; then
  (cd "$backup_directory" && shasum -a 256 -c "$checksum_name")
else
  echo "sha256sum or shasum is required to verify the backup" >&2
  exit 2
fi
case "$restore_database" in
  ''|*[!a-zA-Z0-9_]*) echo "Test database name may contain only letters, digits, and underscores" >&2; exit 2 ;;
esac
if [ "${#restore_database}" -gt 63 ]; then
  echo "Test database name must be at most 63 characters" >&2
  exit 2
fi

project_directory=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
compose_file="$project_directory/compose.yaml"
compose() {
  docker compose --project-directory "$project_directory" -f "$compose_file" "$@"
}

docker_context=${DOCKER_CONTEXT:-$(docker context show)}
context_host=$(docker context inspect "$docker_context" --format '{{.Endpoints.docker.Host}}')
case "$context_host" in
  unix://*|npipe://*) ;;
  *) echo "Restore tests require a local Docker daemon; remote Docker contexts are refused" >&2; exit 2 ;;
esac
if [ -n "${DOCKER_HOST:-}" ] && [ "$DOCKER_HOST" != "$context_host" ]; then
  echo "DOCKER_HOST does not match the selected local Docker context" >&2
  exit 2
fi

postgres_container=$(compose ps -q postgres)
if [ -z "$postgres_container" ]; then
  echo "The local Compose PostgreSQL service is not running" >&2
  exit 2
fi
postgres_image=$(docker inspect --format '{{.Config.Image}}' "$postgres_container")
postgres_service=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "$postgres_container")
postgres_project_directory=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}' "$postgres_container")
postgres_compose_files=$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project.config_files" }}' "$postgres_container")
if [ "$postgres_image" != postgres:15 ] || [ "$postgres_service" != postgres ] \
    || [ "$postgres_project_directory" != "$project_directory" ] \
    || [ "$postgres_compose_files" != "$compose_file" ]; then
  echo "Restore tests require the repository's configured postgres:15 Compose service" >&2
  exit 2
fi
compose exec -T postgres pg_restore --list < "$backup_file" >/dev/null

original_database=$(compose exec -T postgres printenv POSTGRES_DB)
if [ "$restore_database" = "$original_database" ]; then
  echo "Refusing to restore into the configured application database" >&2
  exit 2
fi

database_list=$(compose exec -T postgres sh -c '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  psql -X -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -tA -v ON_ERROR_STOP=1 -c "SELECT datname FROM pg_database"
')
if printf '%s\n' "$database_list" | grep -Fxq -- "$restore_database"; then
  echo "Refusing to overwrite an existing database" >&2
  exit 2
fi

compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" postgres sh -c '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  psql -X -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE \"$RESTORE_TEST_DATABASE\" ENCODING UTF8"
'
compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" postgres sh -c '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  pg_restore -h 127.0.0.1 -U "$POSTGRES_USER" -d "$RESTORE_TEST_DATABASE" \
    --no-owner --no-privileges --exit-on-error
' < "$backup_file"
compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" postgres sh -c '
  export PGPASSWORD="$POSTGRES_PASSWORD"
  psql -X -h 127.0.0.1 -U "$POSTGRES_USER" -d "$RESTORE_TEST_DATABASE" -v ON_ERROR_STOP=1 -tA \
    -c "SELECT \$\$users=\$\$ || (SELECT COUNT(*) FROM users) || \$\$,leave_requests=\$\$ || (SELECT COUNT(*) FROM leave_requests) || \$\$,notifications=\$\$ || (SELECT COUNT(*) FROM notifications)"
'
