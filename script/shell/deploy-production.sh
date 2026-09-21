#!/usr/bin/env bash
set -Eeuo pipefail

# ZSJOS production build, database migration, release and process helper.
# Usage: deploy-production.sh [check|build|db-plan|db-migrate|db-verify|start|stop|restart|health|deploy|rollback]

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$REPO_DIR/.env.production}"
DB_MIGRATOR_PREPARED=false

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
  APP_VERSION="${APP_VERSION:-$(date +%Y.%m.%d-%H%M%S)-$(git -C "$REPO_DIR" rev-parse --short HEAD)}"
  ZSJOS_DB_RELEASE_VERSION="${ZSJOS_DB_RELEASE_VERSION:-$APP_VERSION}"
  export APP_VERSION ZSJOS_DB_RELEASE_VERSION
  TZ="${TZ:-Asia/Shanghai}"
  RELEASES_DIR="${ZSJOS_RELEASES_DIR:-$REPO_DIR/releases}"
  LOG_DIR="${ZSJOS_LOG_DIR:-$REPO_DIR/logs}"
  BACKUP_DIR="${ZSJOS_BACKUP_DIR:-$REPO_DIR/backups}"
  FRONTEND_ADMIN_DIR="${ZSJOS_FRONTEND_ADMIN_DIR:-$REPO_DIR/frontend/admin}"
  FRONTEND_WORKBENCH_DIR="${ZSJOS_FRONTEND_WORKBENCH_DIR:-$REPO_DIR/frontend/workbench}"
  FRONTEND_H5_DIR="${ZSJOS_FRONTEND_H5_DIR:-$REPO_DIR/frontend/h5}"
  FRONTEND_MEDIA_SCREEN_DIR="${ZSJOS_FRONTEND_MEDIA_SCREEN_DIR:-$REPO_DIR/frontend/media-screen}"
  JAR_PATH="${ZSJOS_JAR_PATH:-$REPO_DIR/backend/yudao-server/target/yudao-server.jar}"
  PID_FILE="${ZSJOS_PID_FILE:-$REPO_DIR/zsjos-server.pid}"
  SERVER_PORT="${SERVER_PORT:-48080}"
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
  # The packaged jar contains its production profile; do not force an external
  # config file that may be absent during release switching.
  unset ZSJOS_CONFIG_FILE
  JAVA_OPTS="${JAVA_OPTS:--Xms1g -Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR}"
  DB_COMPOSE_FILE="${ZSJOS_DB_COMPOSE_FILE:-$REPO_DIR/deploy/production/compose.database.yml}"
  DB_ENV_FILE="${ZSJOS_DB_ENV_FILE:-$ENV_FILE}"
  SYSTEMD_SERVICE="${ZSJOS_SYSTEMD_SERVICE:-zsjos-backend.service}"
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
  need_cmd realpath
  need_cmd find
  need_cmd mountpoint
  docker compose version >/dev/null 2>&1 || die "docker compose v2 is required"
  java -version >/dev/null 2>&1
  node --version >/dev/null
  pnpm --version >/dev/null
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
  (cd "$REPO_DIR/backend" && mvn -q clean package -Dmaven.test.skip=true -Dspring-boot.build-image.skip=true)
  [[ -f "$JAR_PATH" ]] || die "backend jar not found: $JAR_PATH"
}

build_admin_frontend() {
  local base_path="$1" out_dir="$2" log_name="$3"
  (cd "$FRONTEND_ADMIN_DIR" && env \
    VITE_APP_TITLE="${VITE_APP_TITLE:-}" VITE_APP_HEAD_TITLE="${VITE_APP_HEAD_TITLE:-}" \
    VITE_BASE_PATH="$base_path" VITE_BASE_URL="${VITE_BASE_URL:-}" \
    VITE_API_URL="${VITE_API_URL:-/admin-api}" VITE_UPLOAD_TYPE="${VITE_UPLOAD_TYPE:-server}" \
    VITE_APP_TENANT_ENABLE="${VITE_APP_TENANT_ENABLE:-true}" VITE_APP_CAPTCHA_ENABLE="${VITE_APP_CAPTCHA_ENABLE:-true}" \
    VITE_DROP_DEBUGGER="${VITE_DROP_DEBUGGER:-true}" VITE_DROP_CONSOLE="${VITE_DROP_CONSOLE:-true}" \
    VITE_SOURCEMAP="${VITE_SOURCEMAP:-false}" VITE_OUT_DIR="$out_dir" \
    VITE_APP_BAIDU_CODE="${VITE_APP_BAIDU_CODE:-}" \
    pnpm build:prod > "$LOG_DIR/$log_name" 2>&1)
}

build_frontends() {
  (cd "$FRONTEND_ADMIN_DIR" && pnpm install --frozen-lockfile --reporter=silent)
  log "building admin frontend"
  build_admin_frontend "${VITE_BASE_PATH:-/admin/}" dist-prod build-admin.log
  log "building embedded admin frontend"
  # The iframe is a separate Vite artifact with its own absolute asset base.
  build_admin_frontend /admin-embed/ dist-embed build-admin-embed.log

  log "building workbench frontend"
  (cd "$FRONTEND_WORKBENCH_DIR" && env \
    VITE_API_BASE_URL="${VITE_API_BASE_URL:-/admin-api}" \
    VITE_ADMIN_EMBED_BASE="${VITE_ADMIN_EMBED_BASE:-/admin-embed/}" \
    VITE_TENANT_ID="${VITE_TENANT_ID:-1}" \
    npm ci --silent && npm run build > "$LOG_DIR/build-workbench.log")

  log "building partner H5 frontend"
  (cd "$FRONTEND_H5_DIR" && env \
    VITE_APP_BASE_API="${VITE_APP_BASE_API:-/part-api}" \
    VITE_APP_REFERENCE_API="${VITE_APP_REFERENCE_API:-/app-api}" \
    VITE_APP_TENANT_ID="${VITE_APP_TENANT_ID:-1}" \
    pnpm install --frozen-lockfile --reporter=silent && pnpm build > "$LOG_DIR/build-h5.log")

  log "building media-screen frontend"
  (cd "$FRONTEND_MEDIA_SCREEN_DIR" && npm ci --silent && env \
    VITE_MEDIA_SCREEN_TENANT_ID="${VITE_MEDIA_SCREEN_TENANT_ID:?media-screen tenant must be configured}" \
    VITE_MEDIA_SCREEN_API_BASE_URL="${VITE_MEDIA_SCREEN_API_BASE_URL:-}" \
    VITE_MEDIA_SCREEN_API_PREFIX="${VITE_MEDIA_SCREEN_API_PREFIX:-/public-api/zsjos/media-screen}" \
    VITE_MEDIA_SCREEN_ENABLE_MOCK=false \
    npm run build > "$LOG_DIR/build-media-screen.log" 2>&1)
}

build() {
  check
  mkdir -p "$LOG_DIR" "$RELEASES_DIR" "$BACKUP_DIR"
  build_backend
  build_frontends
  sha256sum "$JAR_PATH" | awk '{print $1}' > "$RELEASES_DIR/$APP_VERSION.sha256"
  log "artifact checksum saved: $RELEASES_DIR/$APP_VERSION.sha256"
  log "build completed"
}

db_compose() {
  docker compose --env-file "$DB_ENV_FILE" -f "$DB_COMPOSE_FILE" "$@"
}

prepare_db_migrator() {
  [[ "$DB_MIGRATOR_PREPARED" == "true" ]] && return 0
  load_env; check_env; need_cmd bash
  log "building database migrator image from current SQL sources"
  (cd "$REPO_DIR" && bash deploy/production/zsjos-db build-migrator)
  DB_MIGRATOR_PREPARED=true
}

db_plan() {
  prepare_db_migrator
  (cd "$REPO_DIR" && bash deploy/production/zsjos-db plan production)
}

db_migrate() {
  prepare_db_migrator
  (cd "$REPO_DIR" && bash deploy/production/zsjos-db migrate production)
}

db_verify() {
  prepare_db_migrator
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
  mkdir -p "$release_dir/admin-embed" "$release_dir/media-screen"
  cp -a "$FRONTEND_ADMIN_DIR/dist-embed/." "$release_dir/admin-embed/"
  cp -a "$FRONTEND_MEDIA_SCREEN_DIR/dist/." "$release_dir/media-screen/"
  for required in \
    "$release_dir/yudao-server.jar" \
    "$release_dir/admin/index.html" \
    "$release_dir/workbench/index.html" \
    "$release_dir/h5/index.html" \
    "$release_dir/admin-embed/index.html" \
    "$release_dir/media-screen/index.html"; do
    [[ -f "$required" ]] || die "release completeness check failed: missing $required"
  done
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

cleanup_old_releases() {
  local root current previous candidate protected
  root="$(realpath -e -- "$RELEASES_DIR")" || return 1
  # Resolve both retained versions before deleting anything; never guess by mtime.
  if [[ "$root" == / || ! -L "$root/current" || ! -f "$root/previous-release" || -L "$root/previous-release" ]]; then
    warn "release cleanup skipped: invalid release root or retention references"
    return 1
  fi
  current="$(realpath -e -- "$root/current")" || return 1
  previous="$(cat -- "$root/previous-release")"
  if [[ "$previous" != /* || -L "$previous" ]]; then
    warn "release cleanup skipped: invalid previous-release path"
    return 1
  fi
  previous="$(realpath -e -- "$previous")" || return 1
  if [[ "$current" == "$previous" || "${current%/*}" != "$root" || "${previous%/*}" != "$root" \
      || "$current" != "$root/$APP_VERSION" || ! -d "$current" || ! -d "$previous" ]]; then
    warn "release cleanup skipped: retained versions must be distinct direct children of the release root"
    return 1
  fi
  for protected in "$current" "$previous"; do
    if [[ ! -f "$protected/yudao-server.jar" || ! -d "$protected/admin" \
        || ! -d "$protected/workbench" || ! -d "$protected/h5" ]]; then
      warn "release cleanup skipped: retained release is incomplete: $protected"
      return 1
    fi
  done
  local -a candidates=()
  while IFS= read -r -d '' candidate; do
    [[ "$candidate" == "$current" || "$candidate" == "$previous" ]] && continue
    # Recognize installed releases only; leave unrelated directories and symlinks alone.
    [[ -f "$candidate/yudao-server.jar" && ! -L "$candidate/yudao-server.jar" \
        && -d "$candidate/admin" && -d "$candidate/workbench" && -d "$candidate/h5" ]] || continue
    for protected in "$REPO_DIR" "$LOG_DIR" "$BACKUP_DIR"; do
      protected="$(realpath -m -- "$protected")" || return 1
      if [[ "$protected" == "$candidate" || "$protected" == "$candidate/"* ]]; then
        warn "release cleanup skipped: candidate contains a protected source/log/backup path: $candidate"
        return 1
      fi
    done
    if mountpoint -q -- "$candidate"; then
      warn "release cleanup skipped: candidate is a mount point: $candidate"
      return 1
    fi
    candidates+=("$candidate")
  done < <(find "$root" -mindepth 1 -maxdepth 1 -type d -print0)
  for candidate in "${candidates[@]}"; do
    log "removing old release: $candidate"
    rm -rf --one-file-system -- "$candidate" || return 1
  done
  log "release cleanup completed; retained: $current and $previous"
}

stop_server() {
  load_env
  if [[ -n "$SYSTEMD_SERVICE" ]] && systemctl cat "$SYSTEMD_SERVICE" >/dev/null 2>&1; then
    log "stopping backend service=$SYSTEMD_SERVICE"
    systemctl stop "$SYSTEMD_SERVICE"
    return 0
  fi
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
  if [[ -n "$SYSTEMD_SERVICE" ]] && systemctl cat "$SYSTEMD_SERVICE" >/dev/null 2>&1; then
    log "starting backend service=$SYSTEMD_SERVICE"
    systemctl start "$SYSTEMD_SERVICE"
    return 0
  fi
  mkdir -p "$LOG_DIR"
  [[ -f "$RELEASES_DIR/current/yudao-server.jar" ]] || die "current release jar is missing; run build and deploy first"
  [[ -z "$(running_pid)" ]] || die "backend is already running"
  local log_file="$LOG_DIR/server-$(date +%Y%m%d).log"
  log "starting backend on port $SERVER_PORT"
  local redis_password_args=()
  if [[ -n "${REDIS_PASSWORD:-}" ]]; then
    redis_password_args+=("SPRING_DATA_REDIS_PASSWORD=$REDIS_PASSWORD")
  fi
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
    "${redis_password_args[@]}" \
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
  if [[ "$code" != "200" ]]; then
    warn "health check failed: $url ($code)"
    return 1
  fi
  log "health check passed: $url"
}

deploy() {
  # Stop the old release before resource-intensive builds and migrations.
  # Failing to stop must abort the deployment to avoid competing backends.
  stop_server
  build
  # Temporarily skipped during deploy; run db-plan manually when needed.
  # db_plan
  db_migrate
  # Temporarily skipped during deploy; run db-verify manually when needed.
  # db_verify
  install_release
  start_server
  for _ in $(seq 1 120); do
    if health >/dev/null 2>&1; then
      cleanup_old_releases || return 1
      return 0
    fi
    sleep 1
  done
  warn "deployment health checks exhausted; old releases retained"
  return 1
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
  build       Build backend and admin/admin-embed/workbench/H5/media-screen frontends
  db-plan     Rebuild the migrator image and show the read-only production plan
  db-migrate  Rebuild the migrator image and apply pending production migrations
  db-verify   Rebuild the migrator image and verify the production database
  start       Start the current backend release
  stop        Stop the backend
  restart     Restart the backend
  health      Check the local actuator health endpoint
  deploy      Build, migrate, install, start, verify health and retain two releases
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
