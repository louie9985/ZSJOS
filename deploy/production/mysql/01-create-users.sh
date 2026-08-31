#!/usr/bin/env bash
set -euo pipefail

read_secret() {
  local path="$1"
  local value
  value="$(tr -d '\r\n' < "$path")"
  if [[ ! "$value" =~ ^[A-Za-z0-9_@%+=,.-]{24,}$ ]]; then
    echo "Secret $path must contain at least 24 URL-safe characters." >&2
    exit 1
  fi
  printf '%s' "$value"
}

validate_identifier() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[A-Za-z0-9_]{1,32}$ ]]; then
    echo "$name must contain 1-32 letters, numbers, or underscores." >&2
    exit 1
  fi
}

migration_password="$(read_secret /run/secrets/mysql-migration-password)"
app_password="$(read_secret /run/secrets/mysql-app-password)"
migration_user="${ZSJOS_DB_MIGRATION_USER:-zsjos_migrator}"
app_user="${ZSJOS_DB_APP_USER:-zsjos_app}"
validate_identifier MYSQL_DATABASE "${MYSQL_DATABASE}"
validate_identifier ZSJOS_DB_MIGRATION_USER "$migration_user"
validate_identifier ZSJOS_DB_APP_USER "$app_user"

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS '${migration_user}'@'%' IDENTIFIED BY '${migration_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES,
      CREATE VIEW, SHOW VIEW, TRIGGER, EXECUTE, EVENT
ON \`${MYSQL_DATABASE}\`.* TO '${migration_user}'@'%';

CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE
ON \`${MYSQL_DATABASE}\`.* TO '${app_user}'@'%';
FLUSH PRIVILEGES;
SQL
