#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.server.yml"
ENV_FILE="$ROOT_DIR/infra/.env.production"

read_env_value() {
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r'
}

DOMAIN=$(read_env_value DOMAIN)
CERTBOT_EMAIL=$(read_env_value CERTBOT_EMAIL)

if [ -z "${DOMAIN:-}" ] || [ -z "${CERTBOT_EMAIL:-}" ]; then
  echo "DOMAIN and CERTBOT_EMAIL are required."
  exit 1
fi

compose() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "$@"
}

if compose --profile tools run --rm certificate-bootstrap \
  -c "test -f /etc/letsencrypt/renewal/$DOMAIN.conf &&
      test -f /etc/letsencrypt/live/$DOMAIN/fullchain.pem &&
      test -f /etc/letsencrypt/live/$DOMAIN/privkey.pem"; then
  compose up -d reverse-proxy
  exit 0
fi

compose stop reverse-proxy >/dev/null 2>&1 || true
compose --profile tools rm -sf certificate-proxy >/dev/null 2>&1 || true
compose --profile tools up -d certificate-proxy

attempt=0
until curl --silent --show-error --output /dev/null http://127.0.0.1/; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "Certificate proxy did not become ready."
    compose --profile tools logs --tail=100 certificate-proxy
    exit 1
  fi
  sleep 1
done

compose --profile tools run --rm certificate-bootstrap \
  -c "rm -rf /etc/letsencrypt/live/$DOMAIN
      /etc/letsencrypt/archive/$DOMAIN
      /etc/letsencrypt/renewal/$DOMAIN.conf"

compose run --rm --entrypoint certbot certbot \
  certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$CERTBOT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  --force-renewal \
  -d "$DOMAIN"

compose --profile tools rm -sf certificate-proxy
compose up -d reverse-proxy certbot
