#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${RESTORE_FILE:?Set RESTORE_FILE to the verified backup file}"
: "${RESTORE_CONFIRMED:?Set RESTORE_CONFIRMED=YES after stopping writes and preserving the current database}"
[[ "$RESTORE_CONFIRMED" == YES ]]
sha256sum -c "$RESTORE_FILE.sha256"
gzip -t "$RESTORE_FILE"
gzip -dc "$RESTORE_FILE" | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
printf 'Restore completed. Run migration validation, ledger reconciliation and channel checks before reopening writes.\n'
