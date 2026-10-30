#!/bin/sh
# backup.sh schedule   – every day at $BACKUP_TIME (HH:MM, $TZ) make a backup (default)
# backup.sh once       – make a backup now
#
# A backup is two files in /backups: db-<time>.dump (pg_dump custom format) and attachments-<time>.tar.gz.
# With BACKUP_PASSPHRASE they are encrypted (AES-256, .enc). With S3_BUCKET they are copied to
# s3://$S3_BUCKET/$S3_PREFIX/ (S3_ENDPOINT for R2, B2, Spaces, MinIO…). Local copies older than
# LOCAL_RETENTION_DAYS are deleted; old copies in the bucket are removed by a bucket lifecycle rule.
set -eu

BACKUP_DIR=/backups

log() { echo "$(date '+%F %T') backup: $*"; }

s3() {
  if [ -n "${S3_ENDPOINT:-}" ]; then
    aws --endpoint-url "$S3_ENDPOINT" s3 "$@"
  else
    aws s3 "$@"
  fi
}

seal() { # seal FILE -> prints the file to keep (encrypted when a passphrase is set)
  if [ -n "${BACKUP_PASSPHRASE:-}" ]; then
    openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:BACKUP_PASSPHRASE -in "$1" -out "$1.enc"
    rm -f "$1"
    echo "$1.enc"
  else
    echo "$1"
  fi
}

run_backup() {
  stamp=$(date +%Y%m%d-%H%M%S)
  mkdir -p "$BACKUP_DIR"
  db="$BACKUP_DIR/db-$stamp.dump"
  files="$BACKUP_DIR/attachments-$stamp.tar.gz"
  pg_dump --format=custom --compress=9 --no-owner --file="$db.part"
  mv "$db.part" "$db"
  tar -czf "$files" -C /attachments .
  db=$(seal "$db")
  files=$(seal "$files")
  log "created $(basename "$db") ($(du -h "$db" | cut -f1)), $(basename "$files")"
  if [ -n "${S3_BUCKET:-}" ]; then
    for f in "$db" "$files"; do
      s3 cp --only-show-errors "$f" "s3://$S3_BUCKET/${S3_PREFIX:-financemanage}/$(basename "$f")"
    done
    log "copied to s3://$S3_BUCKET/${S3_PREFIX:-financemanage}/"
  fi
  find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'db-*' -o -name 'attachments-*' \) \
    -mtime +"${LOCAL_RETENTION_DAYS:-7}" -delete
}

case "${1:-schedule}" in
  once)
    run_backup
    ;;
  schedule)
    log "daily at ${BACKUP_TIME:-02:30} ($(date +%Z))${S3_BUCKET:+, copied to s3://$S3_BUCKET}"
    last=""
    while true; do
      if [ "$(date +%H:%M)" = "${BACKUP_TIME:-02:30}" ] && [ "$last" != "$(date +%F)" ]; then
        last=$(date +%F)
        # a new process, so that "set -e" stops the backup at the first failing step
        "$0" once || log "FAILED (see above); next try tomorrow or run: backup.sh once"
      fi
      sleep 30
    done
    ;;
  *)
    echo "usage: backup.sh [schedule|once]" >&2
    exit 2
    ;;
esac
