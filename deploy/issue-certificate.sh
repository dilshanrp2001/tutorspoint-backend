#!/usr/bin/env bash
# Issues the first Let's Encrypt certificate for $DOMAIN. Run once, on the first deploy,
# before nginx has ever started: nginx cannot start without a certificate to load, so this
# answers the challenge itself on port 80 (certbot --standalone).
#
# Renewals after that are the certbot service's job, through nginx's webroot.
#
#   ./issue-certificate.sh            # the real thing
#   ./issue-certificate.sh --staging  # Let's Encrypt's staging CA: untrusted certificate, but
#                                     # no rate limit - use it until DNS and ports are right
set -euo pipefail
cd "$(dirname "$0")"

# shellcheck source=env-value.sh
. ./env-value.sh
DOMAIN=$(env_value DOMAIN)
LETSENCRYPT_EMAIL=$(env_value LETSENCRYPT_EMAIL)
: "${DOMAIN:?DOMAIN is not set in .env}"
: "${LETSENCRYPT_EMAIL:?LETSENCRYPT_EMAIL is not set in .env}"

extra=()
if [[ "${1:-}" == "--staging" ]]; then
    extra+=(--staging)
fi

if docker compose ps --status running --services 2>/dev/null | grep -qx nginx; then
    echo "nginx is running and holds port 80. This script is for the first certificate only;" >&2
    echo "renewals are automatic (the certbot service)." >&2
    exit 1
fi

docker compose run --rm -p 80:80 --entrypoint certbot certbot \
    certonly --standalone --non-interactive --agree-tos \
    --email "$LETSENCRYPT_EMAIL" -d "$DOMAIN" "${extra[@]}"

echo "Certificate issued for $DOMAIN. Continue with DEPLOYMENT.md, first deploy step 7."
