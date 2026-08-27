FROM golang:1.25-bookworm AS backup-tools

ENV CGO_ENABLED=0

RUN go install filippo.io/age/cmd/age@v1.2.1 \
    && go install github.com/rclone/rclone@v1.69.1

FROM mysql:9.4

COPY --from=backup-tools /go/bin/age /usr/local/bin/age
COPY --from=backup-tools /go/bin/rclone /usr/local/bin/rclone

RUN age --version && rclone version && mysqldump --version

WORKDIR /backup

COPY backup.sh /backup/backup.sh
RUN chmod 0555 /backup/backup.sh

ENTRYPOINT ["/backup/backup.sh"]
