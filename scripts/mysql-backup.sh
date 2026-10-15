#!/bin/sh
set -eu

: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${BACKUP_RETENTION_DAYS:?BACKUP_RETENTION_DAYS is required}"
: "${BACKUP_INTERVAL_SECONDS:?BACKUP_INTERVAL_SECONDS is required}"
: "${BACKUP_VOLUME_MARKER:?BACKUP_VOLUME_MARKER is required}"

case "$BACKUP_RETENTION_DAYS" in
  ''|*[!0-9]*) echo "BACKUP_RETENTION_DAYS must be a non-negative integer" >&2; exit 2 ;;
esac
case "$BACKUP_INTERVAL_SECONDS" in
  ''|*[!0-9]*|0) echo "BACKUP_INTERVAL_SECONDS must be a positive integer" >&2; exit 2 ;;
esac
if [ "$(cat /backups/.backup-volume-marker 2>/dev/null || true)" != "$BACKUP_VOLUME_MARKER" ]; then
  echo "Backup destination is not the configured mounted backup disk" >&2
  exit 1
fi

attempt=0
while [ "$attempt" -lt 120 ]; do
  table_count=$(MYSQL_PWD="$DB_PASSWORD" mysql --host=mysql --port=3306 --user="$DB_USER" \
    --batch --skip-column-names "$MYSQL_DATABASE" \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('users','leave_requests','notifications')")
  [ "$table_count" = 3 ] && break
  attempt=$((attempt + 1))
  sleep 5
done
if [ "$table_count" != 3 ]; then
  echo "Application schema did not become ready before backup timeout" >&2
  exit 1
fi

umask 077
stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
backup="/backups/leave-management-${stamp}.sql"
temporary="${backup}.partial"
checksum_temporary="/backups/$(basename "$backup").sha256.partial"
trap 'rm -f "$temporary" "$checksum_temporary"' EXIT HUP INT TERM

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host=mysql \
  --port=3306 \
  --user="$DB_USER" \
  --single-transaction \
  --no-tablespaces \
  --triggers \
  --hex-blob \
  "$MYSQL_DATABASE" > "$temporary"
test -s "$temporary"
mv "$temporary" "$backup"
backup_name=$(basename "$backup")
(cd /backups && sha256sum "$backup_name" > "${backup_name}.sha256.partial" && mv "${backup_name}.sha256.partial" "${backup_name}.sha256")
(cd /backups && sha256sum -c "${backup_name}.sha256" >/dev/null)
find /backups -type f -name 'leave-management-*.sql' -mtime "+${BACKUP_RETENTION_DAYS}" -delete
find /backups -type f -name 'leave-management-*.sql.sha256' -mtime "+${BACKUP_RETENTION_DAYS}" -delete
printf 'MySQL backup completed: %s\n' "$backup"
