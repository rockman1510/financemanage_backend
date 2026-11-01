# Nightly backup: pg_dump + receipt photos, optionally encrypted, kept locally and copied to S3-compatible storage
FROM alpine:3.22
RUN apk add --no-cache postgresql17-client aws-cli openssl tzdata
COPY backup.sh restore.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh
ENTRYPOINT ["backup.sh"]
CMD ["schedule"]
