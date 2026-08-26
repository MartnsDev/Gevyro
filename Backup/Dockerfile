FROM alpine:3.22

RUN apk add --no-cache \
    age \
    bash \
    ca-certificates \
    coreutils \
    gzip \
    mariadb-client \
    rclone \
    tzdata

WORKDIR /backup

COPY backup.sh /backup/backup.sh
RUN chmod 0555 /backup/backup.sh

ENTRYPOINT ["/backup/backup.sh"]
