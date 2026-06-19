#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
ENV_FILE="$ROOT_DIR/infra/.env.production"
SERVICE="${1:-}"

if [ ! -f "$ENV_FILE" ]; then
  echo "infra/.env.production is required."
  exit 1
fi

show_logs() {
  compose_file="$1"
  service="${2:-}"

  if [ -n "$service" ]; then
    docker compose \
      --env-file "$ENV_FILE" \
      -f "$compose_file" \
      logs -f --tail=200 "$service"
  else
    docker compose \
      --env-file "$ENV_FILE" \
      -f "$compose_file" \
      logs -f --tail=200
  fi
}

contains_service() {
  compose_file="$1"
  service="$2"

  docker compose \
    --env-file "$ENV_FILE" \
    -f "$compose_file" \
    config --services | grep -qx "$service"
}

if [ -z "$SERVICE" ]; then
  show_logs "$ROOT_DIR/infra/docker-compose.server.yml"
  exit 0
fi

for compose_file in \
  "$ROOT_DIR/infra/docker-compose.server.yml" \
  "$ROOT_DIR/infra/docker-compose.infra.yml" \
  "$ROOT_DIR/infra/docker-compose.monitoring.yml"
do
  if contains_service "$compose_file" "$SERVICE"; then
    show_logs "$compose_file" "$SERVICE"
    exit 0
  fi
done

echo "Unknown service: $SERVICE"
exit 1
