FROM alpine:3.22 AS backup-tools

RUN apk add --no-cache \
    age \
    rclone

FROM mysql:9.4

COPY --from=backup-tools /usr/bin/age /usr/local/bin/age
COPY --from=backup-tools /usr/bin/rclone /usr/local/bin/rclone

WORKDIR /backup

COPY backup.sh /backup/backup.sh
RUN chmod 0555 /backup/backup.sh

ENTRYPOINT ["/backup/backup.sh"]
