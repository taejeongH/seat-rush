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
  -c "test -f /etc/letsencrypt/renewal/$DOMAIN.conf"; then
  exit 0
fi

compose --profile tools run --rm certificate-bootstrap -c "
  apk add --no-cache openssl >/dev/null &&
  mkdir -p /etc/letsencrypt/live/$DOMAIN &&
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout /etc/letsencrypt/live/$DOMAIN/privkey.pem \
    -out /etc/letsencrypt/live/$DOMAIN/fullchain.pem \
    -subj /CN=localhost
"

compose up -d reverse-proxy

attempt=0
until curl --silent --show-error --output /dev/null http://127.0.0.1/; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "Reverse proxy did not become ready."
    compose logs --tail=100 reverse-proxy
    exit 1
  fi
  sleep 1
done

compose --profile tools run --rm certificate-bootstrap \
  -c "rm -rf /etc/letsencrypt/live/$DOMAIN"

compose run --rm --entrypoint certbot certbot \
  certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$CERTBOT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  --force-renewal \
  -d "$DOMAIN"

compose exec reverse-proxy nginx -s reload
