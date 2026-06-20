#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
SERVER_COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.server.yml"
BUILD_COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.build.yml"
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

compose() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$SERVER_COMPOSE_FILE" \
    -f "$BUILD_COMPOSE_FILE" \
    "$@"
}

compose build --pull
compose up -d --remove-orphans \
  frontend api-gateway ticket-service queue-service payment-service notification-consumer

sh "$ROOT_DIR/infra/scripts/initialize-https.sh"

compose up -d --remove-orphans reverse-proxy certbot

# 로컬 빌드 배포도 운영 배포와 동일하게 Nginx upstream을 다시 연결합니다.
compose restart reverse-proxy

compose ps
