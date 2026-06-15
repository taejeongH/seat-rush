#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
COMPOSE_FILE="$ROOT_DIR/infra/docker-compose.infra.yml"
ENV_FILE="$ROOT_DIR/infra/.env.production"
BACKUP_DIR="$ROOT_DIR/infra/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/mysql-$TIMESTAMP.sql"

mkdir -p "$BACKUP_DIR"

docker compose \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump \
    -u root \
    --single-transaction \
    --routines \
    --events \
    --databases "$TICKET_DB_NAME" "$PAYMENT_DB_NAME"' \
  > "$BACKUP_FILE"

gzip "$BACKUP_FILE"
echo "MySQL backup created: $BACKUP_FILE.gz"
