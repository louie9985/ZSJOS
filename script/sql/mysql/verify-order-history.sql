-- UTF-8. Read-only pre-rollout inventory; no names, contacts or complete snapshots returned.
SET NAMES utf8mb4;
SELECT COUNT(*) AS orders_without_round FROM zsjos_order WHERE deleted=b'0' AND current_approval_round_id IS NULL;
SELECT COALESCE(JSON_UNQUOTE(JSON_EXTRACT(order_snapshot,'$.snapshotVersion')),'legacy') AS snapshot_version,
       COUNT(*) AS rounds,
       SUM(JSON_EXTRACT(order_snapshot,'$.submitter.name') IS NULL OR JSON_TYPE(JSON_EXTRACT(order_snapshot,'$.submitter.name'))='NULL') AS missing_submitter_name,
       SUM(JSON_EXTRACT(order_snapshot,'$.orderLabels.paymentMethod') IS NULL OR JSON_TYPE(JSON_EXTRACT(order_snapshot,'$.orderLabels.paymentMethod'))='NULL') AS missing_payment_label
FROM zsjos_order_approval_round WHERE deleted=b'0'
GROUP BY COALESCE(JSON_UNQUOTE(JSON_EXTRACT(order_snapshot,'$.snapshotVersion')),'legacy');
