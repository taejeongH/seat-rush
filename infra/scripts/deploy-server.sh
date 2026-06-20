#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.server.yml"
ENV_FILE="$ROOT_DIR/infra/.env.production"
export COMPOSE_PARALLEL_LIMIT=${COMPOSE_PARALLEL_LIMIT:-1}

if [ ! -f "$ENV_FILE" ]; then
  echo "infra/.env.production is required."
  exit 1
fi

if ! docker network inspect seat-rush-production-network >/dev/null 2>&1; then
  echo "Infrastructure is not running. Run deploy-infra.sh first."
  exit 1
fi

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  pull \
  frontend api-gateway ticket-service queue-service payment-service notification-consumer

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  up -d --remove-orphans \
  frontend api-gateway ticket-service queue-service payment-service notification-consumer

sh "$ROOT_DIR/infra/scripts/initialize-https.sh"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  up -d --remove-orphans reverse-proxy certbot

# Nginx가 새 Docker DNS 설정과 교체된 Gateway/Frontend 컨테이너를 다시 인식하도록 재기동합니다.
docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  restart reverse-proxy

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  ps
