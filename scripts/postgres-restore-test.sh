#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "Usage: scripts/postgres-restore-test.sh BACKUP_FILE NEW_TEST_DATABASE" >&2
  exit 2
fi

backup_file=$1
restore_database=$2
if [ ! -s "$backup_file" ] || [ ! -s "${backup_file}.sha256" ]; then
  echo "Backup file or checksum is missing/empty" >&2
  exit 2
fi
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$(dirname "$backup_file")" && sha256sum -c "$(basename "${backup_file}.sha256")")
elif command -v shasum >/dev/null 2>&1; then
  (cd "$(dirname "$backup_file")" && shasum -a 256 -c "$(basename "${backup_file}.sha256")")
else
  echo "sha256sum or shasum is required to verify the backup" >&2
  exit 2
fi
case "$restore_database" in
  ''|*[!a-zA-Z0-9_]*) echo "Test database name may contain only letters, digits, and underscores" >&2; exit 2 ;;
esac

original_database=$(docker compose exec -T postgres printenv POSTGRES_DB | tr -d '\r')
if [ "$restore_database" = "$original_database" ]; then
  echo "Refusing to restore into the configured application database" >&2
  exit 2
fi

database_list=$(docker compose exec -T postgres sh -c '
  PGPASSWORD="$POSTGRES_PASSWORD" psql -X -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -tA -c "SELECT datname FROM pg_database"
')
if printf '%s\n' "$database_list" | grep -Fxq "$restore_database"; then
  echo "Refusing to overwrite an existing database" >&2
  exit 2
fi

docker compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" postgres sh -c '
  PGPASSWORD="$POSTGRES_PASSWORD" psql -X -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE \"$RESTORE_TEST_DATABASE\" ENCODING UTF8"
'
docker compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" postgres sh -c '
  PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -h 127.0.0.1 -U "$POSTGRES_USER" -d "$RESTORE_TEST_DATABASE" \
    --no-owner --no-privileges --exit-on-error
' < "$backup_file"
docker compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" postgres sh -c '
  PGPASSWORD="$POSTGRES_PASSWORD" psql -X -h 127.0.0.1 -U "$POSTGRES_USER" -d "$RESTORE_TEST_DATABASE" -v ON_ERROR_STOP=1 -tA \
    -c "SELECT \$\$users=\$\$ || (SELECT COUNT(*) FROM users) || \$\$,leave_requests=\$\$ || (SELECT COUNT(*) FROM leave_requests) || \$\$,notifications=\$\$ || (SELECT COUNT(*) FROM notifications)"
'
