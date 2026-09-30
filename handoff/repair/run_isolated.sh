#!/usr/bin/env bash
# Isolated verification of the legacy chain repair against a scratch copy.
#
# Usage: run_isolated.sh <scratch_db> <plan_statements.sql> [--execute]
#
# Without --execute it performs a dry-run: precheck + snapshot + what-if counts,
# no writes to the target rows. With --execute it applies the repair inside the
# scratch database only. The script never touches the production schema.
set -euo pipefail

SCRATCH="${1:?scratch db name required}"
STATEMENTS="${2:?path to plan_statements.sql required}"
MODE="${3:-}"

STAMP="$(date +%Y%m%d%H%M%S)"
TENANT_ID=1
SECRET=/opt/zsjos-runtime/secrets/mysql-root-password
RP="$(cat "$SECRET")"

mysql_run() {
  docker exec -i -e MYSQL_PWD="$RP" zsjos-mysql-1 \
    mysql --default-character-set=utf8mb4 -uroot "$@"
}

echo "=== target scratch db: ${SCRATCH} ==="
mysql_run -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCRATCH}' AND table_name IN ('zsjos_order','zsjos_order_approval_round');"

echo
echo "=== precheck (read-only) ==="
mysql_run -t -e "
SET NAMES utf8mb4;
SELECT 'legacy orders total' AS metric, COUNT(*) AS n
FROM ${SCRATCH}.zsjos_order o
JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
UNION ALL SELECT 'pointer null', COUNT(*)
FROM ${SCRATCH}.zsjos_order o
JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
  AND o.current_approval_round_id IS NULL
UNION ALL SELECT 'orders with >1 legacy round', COUNT(*) FROM (
  SELECT o.id FROM ${SCRATCH}.zsjos_order o
  JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
  WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
  GROUP BY o.id HAVING COUNT(*)>1) t;
"

echo
echo "=== planned changes (derived from the plan file, read-only) ==="
if [ -f /tmp/repair_plan.tsv ]; then
  awk -F'\t' 'NR>1 {print $4}' /tmp/repair_plan.tsv | sort | uniq -c | \
    awk '{printf "  %-28s %s\n", $2, $1}'
  echo "  distinct orders: $(awk -F'\t' 'NR>1 {print $1}' /tmp/repair_plan.tsv | sort -u | wc -l)"
else
  echo "  (no /tmp/repair_plan.tsv; run build_plan.py first)"
fi

echo
echo "=== target-state counts already in the scratch db ==="
mysql_run -t -e "
SET NAMES utf8mb4;
SELECT 'already superseded' AS metric, COUNT(*) AS n
FROM ${SCRATCH}.zsjos_order o
JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
  AND o.status='superseded'
UNION ALL SELECT 'already effective', COUNT(*)
FROM ${SCRATCH}.zsjos_order o
JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
  AND o.status='effective'
UNION ALL SELECT 'already terminated', COUNT(*)
FROM ${SCRATCH}.zsjos_order o
JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
  AND o.status='terminated'
UNION ALL SELECT 'already pending_approval', COUNT(*)
FROM ${SCRATCH}.zsjos_order o
JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
  AND o.status='pending_approval';
"

if [ "$MODE" != "--execute" ]; then
  echo
  echo "dry-run only; pass --execute to apply inside ${SCRATCH}"
  exit 0
fi

echo
echo "=== applying repair inside ${SCRATCH} ==="
SNAP="zsjos_order_bak_${STAMP}"
{
  echo "SET NAMES utf8mb4;"
  echo "SET @TENANT_ID=${TENANT_ID};"
  echo "SET @STAMP='${STAMP}';"
  echo "DROP TABLE IF EXISTS ${SCRATCH}.${SNAP};"
  echo "CREATE TABLE ${SCRATCH}.${SNAP} AS"
  echo "SELECT o.* FROM ${SCRATCH}.zsjos_order o"
  echo "JOIN ${SCRATCH}.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'"
  echo "WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review';"
  # pointer backfill: only rows that still have no pointer
  echo "UPDATE ${SCRATCH}.zsjos_order o"
  echo "JOIN ("
  echo "  SELECT r.order_id, MIN(r.id) AS round_id"
  echo "  FROM ${SCRATCH}.zsjos_order_approval_round r"
  echo "  WHERE r.tenant_id=${TENANT_ID} AND r.deleted=b'0'"
  echo "    AND r.process_definition_key='legacy-finance-review'"
  echo "  GROUP BY r.order_id"
  echo ") m ON m.order_id = o.id"
  echo "SET o.current_approval_round_id = m.round_id"
  echo "WHERE o.tenant_id=${TENANT_ID} AND o.deleted=b'0' AND o.current_approval_round_id IS NULL;"
  sed "s/@TENANT_ID@/${TENANT_ID}/g" "$STATEMENTS"
} > "/tmp/apply_${STAMP}.sql"

mysql_run < "/tmp/apply_${STAMP}.sql"
echo "backup table: ${SCRATCH}.${SNAP}"
echo "applied script: /tmp/apply_${STAMP}.sql"
