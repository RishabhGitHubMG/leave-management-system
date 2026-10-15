#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "Usage: scripts/mysql-restore-test.sh BACKUP_FILE EMPTY_TEST_DATABASE" >&2
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

original_database=$(docker compose exec -T mysql printenv MYSQL_DATABASE | tr -d '\r')
if [ "$restore_database" = "$original_database" ]; then
  echo "Refusing to restore into the configured application database" >&2
  exit 2
fi

database_list=$(docker compose exec -T mysql sh -c '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names -e "SHOW DATABASES"
')
if printf '%s\n' "$database_list" | grep -Fxq "$restore_database"; then
  echo "Refusing to overwrite an existing test database" >&2
  exit 2
fi

docker compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" mysql sh -c '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e \
    "CREATE DATABASE $RESTORE_TEST_DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
'
docker compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" mysql sh -c '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$RESTORE_TEST_DATABASE"
' < "$backup_file"
docker compose exec -T -e RESTORE_TEST_DATABASE="$restore_database" mysql sh -c '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names "$RESTORE_TEST_DATABASE" -e \
    "SELECT CONCAT(\"users=\", (SELECT COUNT(*) FROM users), \",leave_requests=\", (SELECT COUNT(*) FROM leave_requests), \",notifications=\", (SELECT COUNT(*) FROM notifications))"
'
