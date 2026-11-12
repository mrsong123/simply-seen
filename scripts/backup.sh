#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${BACKUP_DIR:?Set BACKUP_DIR to an encrypted backup mount}"
mkdir -p "$BACKUP_DIR"
umask 077
backup_file="$BACKUP_DIR/sujian-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u "$MYSQL_USER" --single-transaction --routines --triggers --no-tablespaces "$MYSQL_DATABASE"' | gzip > "$backup_file"
gzip -t "$backup_file"
sha256sum "$backup_file" > "$backup_file.sha256"
printf 'Backup written: %s\n' "$backup_file"
