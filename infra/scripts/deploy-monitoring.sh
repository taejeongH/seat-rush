#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
ENV_FILE="$ROOT_DIR/infra/.env.production"
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.monitoring.yml"

if [ ! -f "$ENV_FILE" ]; then
  echo "infra/.env.production is required."
  exit 1
fi

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  pull

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  up -d

# Bind mount로 변경된 Prometheus 설정을 프로세스에 다시 로드합니다.
docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  restart prometheus

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  ps
