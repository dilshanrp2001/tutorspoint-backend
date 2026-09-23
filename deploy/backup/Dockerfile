# Nightly off-server backups, restores and restore drills (tp-backup). Built on the server by
# `docker compose build`; the only thing here is the tooling, never a credential.
#
# pg_dump's major version must be at least the server's: this image and the postgres service
# both move to a new major together (DEPLOYMENT.md, "Upgrading PostgreSQL").
FROM postgres:16-alpine

RUN apk add --no-cache bash age rclone curl tar coreutils

COPY --chmod=755 tp-backup /usr/local/bin/tp-backup

ENTRYPOINT ["tp-backup"]
CMD ["schedule"]
