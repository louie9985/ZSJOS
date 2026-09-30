#!/usr/bin/env bash
# Rebuild every analysis input from PRODUCTION, read-only.
#
# The scratch databases have been written to during earlier verification and
# their rows no longer reflect production. Anything used to build a repair plan
# must come from the production schema directly.
set -euo pipefail

SECRET=/opt/zsjos-runtime/secrets/mysql-root-password
RP="$(cat "$SECRET")"

mysql_prod() {
  docker exec -i -e MYSQL_PWD="$RP" zsjos-mysql-1 \
    mysql --default-character-set=utf8mb4 -uroot -N "$@"
}

echo "exporting legacy-mapped orders ..."
mysql_prod -e "
SELECT o.order_no, o.id, o.status, o.current_approval_round_id, o.supersedes_order_id,
       o.superseded_by_order_id,
       JSON_EXTRACT(r.order_snapshot,'\$.legacyOrderId'),
       JSON_UNQUOTE(JSON_EXTRACT(r.order_snapshot,'\$.legacyStatus')), r.status
FROM zsjos.zsjos_order o
JOIN zsjos.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=1 AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review'
ORDER BY o.id;" > /tmp/zsjos_legacy.tsv

echo "exporting legacy round id map ..."
mysql_prod -e "
SELECT JSON_EXTRACT(r.order_snapshot,'\$.legacyOrderId'), r.id
FROM zsjos.zsjos_order o
JOIN zsjos.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=1 AND o.deleted=b'0' AND r.process_definition_key='legacy-finance-review';" > /tmp/round_map.tsv

echo "exporting every effective order still missing effective_at ..."
mysql_prod -e "
SELECT o.order_no, o.id, o.create_time, o.status,
       IFNULL(r.id,'-'), IFNULL(JSON_EXTRACT(r.order_snapshot,'\$.legacyOrderId'),'-'),
       IFNULL(r.status,'-'), IFNULL(r.completed_at,'-'), IFNULL(r.process_definition_key,'-')
FROM zsjos.zsjos_order o
LEFT JOIN zsjos.zsjos_order_approval_round r ON r.order_id=o.id AND r.deleted=b'0'
WHERE o.tenant_id=1 AND o.deleted=b'0' AND o.status='effective' AND o.effective_at IS NULL
ORDER BY o.order_no;" > /tmp/eff_gap.tsv

wc -l /tmp/zsjos_legacy.tsv /tmp/round_map.tsv /tmp/eff_gap.tsv
