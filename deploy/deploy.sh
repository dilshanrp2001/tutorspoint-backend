#!/usr/bin/env bash
# Releases (or rolls back to) a pair of image tags. DEPLOYMENT.md, "Releasing" and "Rollback".
#
#   ./deploy.sh <backend-tag> <frontend-tag>      e.g. ./deploy.sh sha-1a2b3c4 sha-5d6e7f8
#   ./deploy.sh --no-backup <backend-tag> <frontend-tag>
#
# 1. pulls both images first, so a mistyped tag fails before anything has changed;
# 2. takes a `predeploy` backup, when backups are on - the way back if this release's
#    migrations must be undone;
# 3. writes the tags to .env and appends the change to releases.log;
# 4. recreates the containers and waits for the backend's healthcheck (Flyway has run, and
#    Hibernate has validated the schema, by the time it reports healthy).
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=env-value.sh
. ./env-value.sh

backup=1
# Backups are opt-in (compose.yml): without the backup profile there is nothing to take one with.
if [[ ",$(env_value COMPOSE_PROFILES)," != *,backup,* ]]; then
    backup=0
    printf 'Backups are off (no COMPOSE_PROFILES=backup in .env): no predeploy backup. A release\n' >&2
    printf 'whose migration goes wrong can only be rolled forward - DEPLOYMENT.md, "Rollback".\n' >&2
fi
if [[ "${1:-}" == "--no-backup" ]]; then
    backup=0
    shift
fi
[[ $# -eq 2 ]] || { sed -n '4,5p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
new_backend=$1
new_frontend=$2
old_backend=$(env_value BACKEND_TAG)
old_frontend=$(env_value FRONTEND_TAG)

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

log "pulling backend:$new_backend and frontend:$new_frontend"
BACKEND_TAG=$new_backend FRONTEND_TAG=$new_frontend docker compose pull backend nginx

if (( backup )); then
    log "taking a predeploy backup"
    docker compose run --rm backup backup predeploy
fi

set_env_value BACKEND_TAG "$new_backend"
set_env_value FRONTEND_TAG "$new_frontend"
printf '%s backend=%s frontend=%s (was backend=%s frontend=%s)\n' \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$new_backend" "$new_frontend" \
    "${old_backend:-none}" "${old_frontend:-none}" >> releases.log

log "starting the new containers"
docker compose up -d --build --remove-orphans

backend_container=$(docker compose ps -q backend)
for _ in $(seq 1 60); do
    health=$(docker inspect -f '{{.State.Health.Status}}' "$backend_container" 2>/dev/null || echo unknown)
    restarts=$(docker inspect -f '{{.RestartCount}}' "$backend_container" 2>/dev/null || echo 0)
    if [[ "$health" == healthy ]]; then
        log "backend is healthy"
        break
    fi
    # A JVM that refuses to start (a schema mismatch, a missing variable) exits, and the restart
    # policy starts it again: one restart already means this release will not come up.
    if (( restarts > 0 )); then
        health="exited and restarted $restarts time(s)"
        break
    fi
    sleep 5
done

if [[ "$health" != healthy ]]; then
    log "backend did not become healthy ($health). Last log lines:"
    docker compose logs --tail 40 backend
    if docker compose logs backend 2>&1 | grep -q 'Detected applied migration not resolved locally'; then
        cat >&2 <<EOF

The database has a migration this image does not know: it is older than the schema. Either
deploy an image at least as new as the schema, or restore the predeploy backup taken before
that migration ran - DEPLOYMENT.md, "Rollback".
EOF
    else
        cat >&2 <<EOF

The release did not start. To go back to what was running before:
    ./deploy.sh --no-backup ${old_backend:-<previous-backend-tag>} ${old_frontend:-<previous-frontend-tag>}
If that backend then refuses to start because this release applied a migration, see
DEPLOYMENT.md, "Rollback".
EOF
    fi
    exit 1
fi

domain=$(env_value DOMAIN)
if curl -fsS -o /dev/null "https://$domain/" && curl -fsS -o /dev/null "https://$domain/api/reference"; then
    log "https://$domain/ and /api/reference answer. Released backend=$new_backend frontend=$new_frontend"
else
    log "WARNING: the backend is healthy but https://$domain/ or /api/reference did not answer - check nginx"
    exit 1
fi
