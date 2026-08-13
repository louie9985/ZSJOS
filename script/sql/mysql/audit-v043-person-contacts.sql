-- Read-only preflight for V043. Any returned row blocks the migration.
SELECT 'blank_contact' issue,tenant_id,id person_id,mobile,wechat_id FROM zsjos_person
WHERE deleted=b'0' AND ((mobile IS NOT NULL AND TRIM(mobile)='') OR (wechat_id IS NOT NULL AND TRIM(wechat_id)=''));
SELECT 'wechat_over_64' issue,tenant_id,id person_id,mobile,wechat_id FROM zsjos_person
WHERE deleted=b'0' AND wechat_id IS NOT NULL AND CHAR_LENGTH(TRIM(wechat_id))>64;
SELECT tenant_id,contact_value,GROUP_CONCAT(DISTINCT person_id ORDER BY person_id) person_ids FROM (
  SELECT tenant_id,id person_id,CONVERT(TRIM(mobile) USING utf8mb4) COLLATE utf8mb4_bin contact_value FROM zsjos_person WHERE deleted=b'0' AND mobile IS NOT NULL
  UNION ALL
  SELECT tenant_id,id,CONVERT(TRIM(wechat_id) USING utf8mb4) COLLATE utf8mb4_bin FROM zsjos_person WHERE deleted=b'0' AND wechat_id IS NOT NULL
) contacts GROUP BY tenant_id,contact_value HAVING COUNT(DISTINCT person_id)>1;
