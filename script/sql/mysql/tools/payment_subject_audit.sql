-- UTF-8. Read-only pre-release audit; no schema/data/permission changes.
-- Prerequisites: existing payment-subject and zsjos_payment_order, payment-subject and gateway-event tables.
-- Execute against the intended database with a UTF-8 client. Default scope: tenant 1.
-- Repeatable SELECTs only; no rollback required. Never output keys, tokens or full snapshots.
SET NAMES utf8mb4;
SET @payment_audit_tenant = COALESCE(@payment_audit_tenant, 1);

-- Required route codes, independent of is_default. Each code should have one enabled complete row.
-- Credential completeness is not RSA validation or proof of platform authorization.
SELECT codes.subject_code, COUNT(s.id) AS existing_subjects,
       SUM(CASE WHEN s.status = 0 THEN 1 ELSE 0 END) AS enabled_subjects,
       SUM(CASE WHEN s.status = 0 AND COALESCE(TRIM(s.cusid), '') <> ''
           AND COALESCE(TRIM(s.appid), '') <> ''
           AND COALESCE(TRIM(s.merchant_private_key), '') <> ''
           AND COALESCE(TRIM(s.platform_public_key), '') <> '' THEN 1 ELSE 0 END) AS enabled_complete_subjects
FROM (SELECT 'school' AS subject_code UNION ALL SELECT 'company') codes
LEFT JOIN zsjos_payment_subject s ON s.subject_code = codes.subject_code
  AND s.tenant_id = @payment_audit_tenant AND s.deleted = 0
GROUP BY codes.subject_code;

SELECT COUNT(*) AS broken_product_subject_associations
FROM zsjos_product_payment_subject r
LEFT JOIN zsjos_payment_subject s
  ON s.id = r.payment_subject_id AND s.tenant_id = r.tenant_id AND s.deleted = 0
WHERE r.tenant_id = @payment_audit_tenant AND r.deleted = 0
  AND (s.id IS NULL OR s.status <> 0 OR s.status IS NULL
       OR COALESCE(TRIM(s.cusid), '') = '' OR COALESCE(TRIM(s.appid), '') = ''
       OR COALESCE(TRIM(s.merchant_private_key), '') = ''
       OR COALESCE(TRIM(s.platform_public_key), '') = '');

SELECT COUNT(*) AS scoped_payment_count
FROM zsjos_payment_order
WHERE tenant_id = @payment_audit_tenant AND deleted = 0;

-- Completeness only; Java validates key encoding. A complete snapshot is not proof of actual merchant.
SELECT status, COUNT(*) AS payment_count,
       SUM(CASE WHEN COALESCE(TRIM(reqsn), '') = '' THEN 1 ELSE 0 END) AS no_gateway_request_number,
       SUM(CASE WHEN COALESCE(JSON_VALID(subject_snapshot_json), 0) = 0 THEN 1 ELSE 0 END) AS missing_or_malformed_snapshot,
       SUM(CASE WHEN JSON_VALID(subject_snapshot_json) = 1 AND
           (COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(subject_snapshot_json, '$.cusid')), 'null'), '') = ''
            OR COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(subject_snapshot_json, '$.appid')), 'null'), '') = ''
            OR COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(subject_snapshot_json, '$.merchantPrivateKey')), 'null'), '') = ''
            OR COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(subject_snapshot_json, '$.platformPublicKey')), 'null'), '') = '')
           THEN 1 ELSE 0 END) AS incomplete_snapshot
FROM zsjos_payment_order
WHERE tenant_id = @payment_audit_tenant AND deleted = 0
GROUP BY status;

-- Compare the frozen merchant with recorded outbound WeChat fields; other channels need platform evidence.
SELECT COUNT(DISTINCT p.id) AS payments_with_recorded_merchant_mismatch
FROM zsjos_payment_order p
JOIN zsjos_payment_gateway_event e ON e.payment_order_id = p.id AND e.tenant_id = p.tenant_id AND e.deleted = 0
WHERE p.tenant_id = @payment_audit_tenant AND p.deleted = 0
  AND e.event_type = 'payment_wechat_order'
  AND JSON_VALID(p.subject_snapshot_json) = 1 AND JSON_VALID(e.request_payload) = 1
  AND (NOT (JSON_EXTRACT(p.subject_snapshot_json, '$.cusid') <=> JSON_EXTRACT(e.request_payload, '$.cusid'))
       OR NOT (JSON_EXTRACT(p.subject_snapshot_json, '$.appid') <=> JSON_EXTRACT(e.request_payload, '$.appid')));
