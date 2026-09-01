#!/usr/bin/env bash
set -Eeuo pipefail

# ZSJOS production build, database migration, release and process helper.
# Usage: deploy-production.sh [check|build|db-plan|db-migrate|db-verify|start|stop|restart|health|deploy|rollback]

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$REPO_DIR/.env.production}"

die() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }
log() { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }

load_env() {
  [[ -f "$ENV_FILE" ]] || die "environment file not found: $ENV_FILE"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  APP_NAME="${APP_NAME:-zsjos}"
  APP_VERSION="${APP_VERSION:-$(date +%Y.%m.%d-%H%M%S)}"
  TZ="${TZ:-Asia/Shanghai}"
  RELEASES_DIR="${ZSJOS_RELEASES_DIR:-$REPO_DIR/releases}"
  LOG_DIR="${ZSJOS_LOG_DIR:-$REPO_DIR/logs}"
  BACKUP_DIR="${ZSJOS_BACKUP_DIR:-$REPO_DIR/backups}"
  FRONTEND_ADMIN_DIR="${ZSJOS_FRONTEND_ADMIN_DIR:-$REPO_DIR/frontend/admin}"
  FRONTEND_WORKBENCH_DIR="${ZSJOS_FRONTEND_WORKBENCH_DIR:-$REPO_DIR/frontend/workbench}"
  FRONTEND_H5_DIR="${ZSJOS_FRONTEND_H5_DIR:-$REPO_DIR/frontend/h5}"
  JAR_PATH="${ZSJOS_JAR_PATH:-$REPO_DIR/backend/yudao-server/target/yudao-server.jar}"
  PID_FILE="${ZSJOS_PID_FILE:-$REPO_DIR/zsjos-server.pid}"
  SERVER_PORT="${SERVER_PORT:-48080}"
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
  JAVA_OPTS="${JAVA_OPTS:--Xms1g -Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR}"
  DB_COMPOSE_FILE="${ZSJOS_DB_COMPOSE_FILE:-$REPO_DIR/deploy/production/compose.database.yml}"
  DB_ENV_FILE="${ZSJOS_DB_ENV_FILE:-$ENV_FILE}"
  SYSTEMD_SERVICE="${ZSJOS_SYSTEMD_SERVICE:-}"
  DB_URL="${ZSJOS_DB_URL:-jdbc:mysql://127.0.0.1:3306/${ZSJOS_DB_NAME:-zsjos}?useSSL=false&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true}"
  DB_HOST="${ZSJOS_DB_HOST:-127.0.0.1}"
  DB_PORT="${ZSJOS_DB_PORT:-3306}"
  REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
  REDIS_PORT="${REDIS_PORT:-6379}"
  REDIS_DATABASE="${REDIS_DATABASE:-1}"
}

need_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing command: $1"; }

check_env() {
  local required=(ZSJOS_DB_NAME ZSJOS_DB_APP_USER ZSJOS_DB_MIGRATION_USER)
  local name
  for name in "${required[@]}"; do
    [[ -n "${!name:-}" ]] || warn "$name is empty"
  done
  [[ -n "${ZSJOS_WECOM_WORKBENCH_BASE_URL:-}" ]] || warn "ZSJOS_WECOM_WORKBENCH_BASE_URL is empty"
  [[ -n "${ZSJOS_WECOM_PARTNER_H5_BASE_URL:-}" ]] || warn "ZSJOS_WECOM_PARTNER_H5_BASE_URL is empty"
  [[ "$ENV_FILE" != *frontend* ]] || die "do not use a frontend .env as the production environment file"
  [[ -f "$DB_COMPOSE_FILE" ]] || die "database compose file not found: $DB_COMPOSE_FILE"
}

check_tools() {
  need_cmd bash
  need_cmd curl
  need_cmd git
  need_cmd java
  need_cmd mvn
  need_cmd node
  need_cmd npm
  need_cmd pnpm
  need_cmd docker
  need_cmd sha256sum
  docker compose version >/dev/null 2>&1 || die "docker compose v2 is required"
  java -version 2>&1 | head -n 1
  node --version
  pnpm --version
}

check() {
  load_env
  check_env
  check_tools
  [[ "$(git -C "$REPO_DIR" branch --show-current)" == "main" || "${ALLOW_NON_MAIN:-false}" == "true" ]] || warn "current branch is not main"
  log "environment: $ENV_FILE"
  log "release: $APP_VERSION"
  log "repository: $REPO_DIR"
}

build_backend() {
  log "building backend"
  (cd "$REPO_DIR/backend" && mvn clean package -Dmaven.test.skip=true -Dspring-boot.build-image.skip=true)
  [[ -f "$JAR_PATH" ]] || die "backend jar not found: $JAR_PATH"
}

build_frontends() {
  log "building admin frontend"
  (cd "$FRONTEND_ADMIN_DIR" && env \
    VITE_APP_TITLE="${VITE_APP_TITLE:-}" VITE_APP_HEAD_TITLE="${VITE_APP_HEAD_TITLE:-}" \
    VITE_BASE_PATH="${VITE_BASE_PATH:-/admin/}" VITE_BASE_URL="${VITE_BASE_URL:-}" \
    VITE_API_URL="${VITE_API_URL:-/admin-api}" VITE_UPLOAD_TYPE="${VITE_UPLOAD_TYPE:-server}" \
    VITE_APP_TENANT_ENABLE="${VITE_APP_TENANT_ENABLE:-true}" VITE_APP_CAPTCHA_ENABLE="${VITE_APP_CAPTCHA_ENABLE:-true}" \
    VITE_DROP_DEBUGGER="${VITE_DROP_DEBUGGER:-true}" VITE_DROP_CONSOLE="${VITE_DROP_CONSOLE:-true}" \
    VITE_SOURCEMAP="${VITE_SOURCEMAP:-false}" VITE_OUT_DIR="${VITE_OUT_DIR:-dist-prod}" \
    VITE_APP_BAIDU_CODE="${VITE_APP_BAIDU_CODE:-}" \
    pnpm install --frozen-lockfile && pnpm build:prod)

  log "building workbench frontend"
  (cd "$FRONTEND_WORKBENCH_DIR" && env \
    VITE_API_BASE_URL="${VITE_API_BASE_URL:-/admin-api}" \
    VITE_ADMIN_EMBED_BASE="${VITE_ADMIN_EMBED_BASE:-/admin-embed/}" \
    VITE_TENANT_ID="${VITE_TENANT_ID:-1}" \
    npm ci && npm run build)

  log "building partner H5 frontend"
  (cd "$FRONTEND_H5_DIR" && env \
    VITE_APP_BASE_API="${VITE_APP_BASE_API:-/part-api}" \
    VITE_APP_REFERENCE_API="${VITE_APP_REFERENCE_API:-/app-api}" \
    VITE_APP_TENANT_ID="${VITE_APP_TENANT_ID:-1}" \
    pnpm install --frozen-lockfile && pnpm build)
}

build() {
  check
  mkdir -p "$LOG_DIR" "$RELEASES_DIR" "$BACKUP_DIR"
  build_backend
  build_frontends
  sha256sum "$JAR_PATH" | tee "$RELEASES_DIR/$APP_VERSION.sha256"
  log "build completed"
}

db_compose() {
  docker compose --env-file "$DB_ENV_FILE" -f "$DB_COMPOSE_FILE" "$@"
}

db_plan() {
  load_env; check_env; need_cmd bash
  (cd "$REPO_DIR" && bash deploy/production/zsjos-db plan production)
}

db_migrate() {
  load_env; check_env; need_cmd bash
  (cd "$REPO_DIR" && bash deploy/production/zsjos-db migrate production)
}

db_verify() {
  load_env; check_env; need_cmd bash
  (cd "$REPO_DIR" && bash deploy/production/zsjos-db verify production)
}

install_release() {
  local release_dir="$RELEASES_DIR/$APP_VERSION"
  local old_release=""
  [[ -L "$RELEASES_DIR/current" ]] && old_release="$(readlink -f "$RELEASES_DIR/current")"
  mkdir -p "$release_dir/admin" "$release_dir/workbench" "$release_dir/h5" "$release_dir/logs"
  cp "$JAR_PATH" "$release_dir/yudao-server.jar"
  cp -a "$FRONTEND_ADMIN_DIR/dist-prod/." "$release_dir/admin/"
  cp -a "$FRONTEND_WORKBENCH_DIR/dist/." "$release_dir/workbench/"
  cp -a "$FRONTEND_H5_DIR/dist/." "$release_dir/h5/"
  # admin-embed / media-screen are not built by this script; carry them over
  # from the current release so nginx keeps serving them after the switch.
  if [[ -n "$old_release" && "$old_release" != "$release_dir" ]]; then
    for extra in admin-embed media-screen; do
      [[ -d "$old_release/$extra" ]] && cp -a "$old_release/$extra" "$release_dir/"
    done
  fi
  [[ -n "$old_release" && "$old_release" != "$release_dir" ]] && printf '%s\n' "$old_release" > "$RELEASES_DIR/previous-release"
  ln -sfn "$release_dir" "$RELEASES_DIR/current"
  ln -sfn "$release_dir/yudao-server.jar" "$REPO_DIR/yudao-server.jar"
  log "release installed: $release_dir"
}

running_pid() {
  [[ -f "$PID_FILE" ]] || return 0
  local pid
  pid="$(cat "$PID_FILE")"
  kill -0 "$pid" 2>/dev/null && printf '%s\n' "$pid" || true
}

stop_server() {
  load_env
  local pid
  pid="$(running_pid)"
  if [[ -z "$pid" ]]; then
    log "backend is not running"
    rm -f "$PID_FILE"
    return 0
  fi
  log "stopping backend pid=$pid"
  kill -TERM "$pid"
  for _ in $(seq 1 120); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    warn "backend did not stop gracefully; sending KILL"
    kill -KILL "$pid"
  fi
  rm -f "$PID_FILE"
}

start_server() {
  load_env
  mkdir -p "$LOG_DIR"
  [[ -f "$RELEASES_DIR/current/yudao-server.jar" ]] || die "current release jar is missing; run build and deploy first"
  [[ -z "$(running_pid)" ]] || die "backend is already running"
  local log_file="$LOG_DIR/server-$(date +%Y%m%d).log"
  log "starting backend on port $SERVER_PORT"
  nohup env \
    TZ="$TZ" \
    SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
    SERVER_PORT="$SERVER_PORT" \
    SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL="$DB_URL" \
    SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME="${ZSJOS_DB_APP_USER:-}" \
    SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD="${ZSJOS_DB_APP_PASSWORD:-}" \
    SPRING_DATASOURCE_DYNAMIC_DATASOURCE_SLAVE_URL="$DB_URL" \
    SPRING_DATASOURCE_DYNAMIC_DATASOURCE_SLAVE_USERNAME="${ZSJOS_DB_APP_USER:-}" \
    SPRING_DATASOURCE_DYNAMIC_DATASOURCE_SLAVE_PASSWORD="${ZSJOS_DB_APP_PASSWORD:-}" \
    SPRING_DATA_REDIS_HOST="$REDIS_HOST" \
    SPRING_DATA_REDIS_PORT="$REDIS_PORT" \
    SPRING_DATA_REDIS_DATABASE="$REDIS_DATABASE" \
    SPRING_DATA_REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
    ZSJOS_WECOM_WORKBENCH_BASE_URL="${ZSJOS_WECOM_WORKBENCH_BASE_URL:-}" \
    ZSJOS_WECOM_PARTNER_H5_BASE_URL="${ZSJOS_WECOM_PARTNER_H5_BASE_URL:-}" \
    ZSJOS_PUBLIC_H5_BASE_URL="${ZSJOS_PUBLIC_H5_BASE_URL:-}" \
    java $JAVA_OPTS -jar "$RELEASES_DIR/current/yudao-server.jar" \
    --server.port="$SERVER_PORT" \
    --spring.profiles.active="$SPRING_PROFILES_ACTIVE" \
    >> "$log_file" 2>&1 &
  echo $! > "$PID_FILE"
  log "backend pid=$(cat "$PID_FILE")"
}

health() {
  load_env
  local url="${HEALTH_CHECK_URL:-http://127.0.0.1:$SERVER_PORT/actuator/health}"
  local code
  code="$(curl -k -L -sS -o /dev/null -w '%{http_code}' --max-time 10 "$url" || true)"
  [[ "$code" == "200" ]] || die "health check failed: $url ($code)"
  log "health check passed: $url"
}

deploy() {
  build
  db_plan
  db_migrate
  db_verify
  install_release
  stop_server || true
  start_server
  for _ in $(seq 1 120); do
    if health >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  health
}

rollback() {
  load_env
  local current previous
  current="$(readlink -f "$RELEASES_DIR/current")"
  previous="$(cat "$RELEASES_DIR/previous-release" 2>/dev/null || true)"
  [[ -n "$previous" && -d "$previous" && "$previous" != "$current" ]] || die "no previous release is available"
  ln -sfn "$previous" "$RELEASES_DIR/current"
  stop_server || true
  start_server
  health
  warn "database was not rolled back; verify application/schema compatibility"
}

usage() {
  cat <<'EOF'
Usage: deploy-production.sh <command>

Commands:
  check       Validate environment, tools and required files
  build       Build backend and admin/workbench/H5 frontends
  db-plan     Show read-only production migration plan
  db-migrate  Apply pending production migrations
  db-verify   Verify production database after migration
  start       Start the current backend release
  stop        Stop the backend
  restart     Restart the backend
  health      Check the local actuator health endpoint
  deploy      Build, migrate, verify, install and start a release
  rollback    Switch to the previous application release (database unchanged)
EOF
}

main() {
  local command="${1:-help}"
  case "$command" in
    check) check ;; build) build ;; db-plan) db_plan ;; db-migrate) db_migrate ;;
    db-verify) db_verify ;; start) start_server ;; stop) stop_server ;;
    restart) stop_server; start_server ;; health) health ;; deploy) deploy ;;
    rollback) rollback ;; help|-h|--help) usage ;; *) usage; die "unknown command: $command" ;;
  esac
}

main "$@"
