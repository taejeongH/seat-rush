#!/usr/bin/env sh
set -eu

validate_identifier() {
  case "$2" in
    ""|*[!A-Za-z0-9_]*)
      echo "$1 must contain only letters, numbers, and underscores."
      exit 1
      ;;
  esac
}

escape_sql_string() {
  printf '%s' "$1" | sed "s/\\\\/\\\\\\\\/g; s/'/''/g"
}

validate_identifier TICKET_DB_NAME "$TICKET_DB_NAME"
validate_identifier TICKET_DB_USERNAME "$TICKET_DB_USERNAME"
validate_identifier PAYMENT_DB_NAME "$PAYMENT_DB_NAME"
validate_identifier PAYMENT_DB_USERNAME "$PAYMENT_DB_USERNAME"

TICKET_DB_PASSWORD_SQL=$(escape_sql_string "$TICKET_DB_PASSWORD")
PAYMENT_DB_PASSWORD_SQL=$(escape_sql_string "$PAYMENT_DB_PASSWORD")

mysql --protocol=socket -u root -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE DATABASE IF NOT EXISTS \`${TICKET_DB_NAME}\`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS \`${PAYMENT_DB_NAME}\`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS '${TICKET_DB_USERNAME}'@'%'
    IDENTIFIED BY '${TICKET_DB_PASSWORD_SQL}';
ALTER USER '${TICKET_DB_USERNAME}'@'%'
    IDENTIFIED BY '${TICKET_DB_PASSWORD_SQL}';

CREATE USER IF NOT EXISTS '${PAYMENT_DB_USERNAME}'@'%'
    IDENTIFIED BY '${PAYMENT_DB_PASSWORD_SQL}';
ALTER USER '${PAYMENT_DB_USERNAME}'@'%'
    IDENTIFIED BY '${PAYMENT_DB_PASSWORD_SQL}';

GRANT ALL PRIVILEGES ON \`${TICKET_DB_NAME}\`.* TO '${TICKET_DB_USERNAME}'@'%';
GRANT ALL PRIVILEGES ON \`${PAYMENT_DB_NAME}\`.* TO '${PAYMENT_DB_USERNAME}'@'%';

FLUSH PRIVILEGES;
EOSQL
