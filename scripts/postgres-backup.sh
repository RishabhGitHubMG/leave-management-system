#!/bin/sh
set -eu

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:?PGPORT is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
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
table_count=0
while [ "$attempt" -lt 120 ]; do
  table_count=$(psql --no-psqlrc -X -tA -v ON_ERROR_STOP=1 \
    -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('users','leave_requests','notifications')" \
    2>/dev/null || true)
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
temporary=''
checksum_temporary=''
published_backup=''
published_checksum=''
cleanup() {
  [ -z "$temporary" ] || rm -f "$temporary"
  [ -z "$checksum_temporary" ] || rm -f "$checksum_temporary"
  [ -z "$published_backup" ] || rm -f "$published_backup"
  [ -z "$published_checksum" ] || rm -f "$published_checksum"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM

temporary=$(mktemp "/backups/.backup-${stamp}.XXXXXX")
checksum_temporary=$(mktemp /backups/.checksum.XXXXXX)
backup="/backups/leave-management-${stamp}-${temporary##*.}.dump"
pg_dump --format=custom --no-owner --no-privileges --file="$temporary"
test -s "$temporary"
pg_restore --list "$temporary" >/dev/null
if ! ln -T "$temporary" "$backup"; then
  echo "Could not publish backup without overwriting an existing file" >&2
  exit 1
fi
published_backup=$backup
rm "$temporary"
temporary=''
backup_name=$(basename "$backup")
(cd /backups && sha256sum "$backup_name" > "$checksum_temporary")
(cd /backups && sha256sum -c "$(basename "$checksum_temporary")" >/dev/null)
if ! ln -T "$checksum_temporary" "${backup}.sha256"; then
  echo "Could not publish checksum without overwriting an existing file" >&2
  exit 1
fi
published_checksum=${backup}.sha256
rm "$checksum_temporary"
checksum_temporary=''
(cd /backups && sha256sum -c "${backup_name}.sha256" >/dev/null)
published_backup=''
published_checksum=''
find /backups -type f -name 'leave-management-*.dump' -mtime "+${BACKUP_RETENTION_DAYS}" -delete
find /backups -type f -name 'leave-management-*.dump.sha256' -mtime "+${BACKUP_RETENTION_DAYS}" -delete
printf 'PostgreSQL backup completed: %s\n' "$backup"
