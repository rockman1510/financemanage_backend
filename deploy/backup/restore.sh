#!/bin/sh
# restore.sh <db-file> [attachments-file]
# Restores a backup made by backup.sh into the running PostgreSQL (replacing the current data) and, when given,
# the receipt photos. A file name may be a path in /backups or "s3:<name>" to download it from the bucket first.
# Stop the backend before restoring:  docker compose -f docker-compose.prod.yml stop backend
# The attachments volume is mounted read-only in the backup service; restore photos with a one-off container
# (see docs/DEPLOYMENT.md).
set -eu

fetch() { # fetch NAME -> local path (downloaded and decrypted when needed)
  case "$1" in
    s3:*)
      name=${1#s3:}
      if [ -n "${S3_ENDPOINT:-}" ]; then
        aws --endpoint-url "$S3_ENDPOINT" s3 cp --only-show-errors \
          "s3://$S3_BUCKET/${S3_PREFIX:-financemanage}/$name" "/tmp/$name"
      else
        aws s3 cp --only-show-errors "s3://$S3_BUCKET/${S3_PREFIX:-financemanage}/$name" "/tmp/$name"
      fi
      path="/tmp/$name"
      ;;
    /*) path="$1" ;;
    *) path="/backups/$1" ;;
  esac
  case "$path" in
    *.enc)
      openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:BACKUP_PASSPHRASE -in "$path" \
        -out "/tmp/$(basename "${path%.enc}")"
      path="/tmp/$(basename "${path%.enc}")"
      ;;
  esac
  echo "$path"
}

[ $# -ge 1 ] || { echo "usage: restore.sh <db-file> [attachments-file]" >&2; exit 2; }

db=$(fetch "$1")
echo "restoring database from $db into $PGDATABASE@$PGHOST"
pg_restore --clean --if-exists --no-owner --single-transaction --dbname="$PGDATABASE" "$db"
echo "database restored"

if [ $# -ge 2 ]; then
  files=$(fetch "$2")
  if touch /attachments/.restore-check 2>/dev/null; then  # "test -w" is always true for root
    rm -f /attachments/.restore-check
    find /attachments -mindepth 1 -delete
    tar -xzf "$files" -C /attachments
    echo "attachments restored"
  else
    cp "$files" /backups/restore-attachments.tar.gz
    echo "attachments volume is read-only here: extract /backups/restore-attachments.tar.gz (docs/DEPLOYMENT.md)"
  fi
fi
