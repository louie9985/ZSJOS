-- Read-only preflight and post-migration verification for V037.
-- Run before V037 to identify blocking business relationships, then run again after V037.
-- This script does not insert, update, or delete data.

SELECT 'duplicate_active_leads' AS check_name, `tenant_id`, `person_id`, COUNT(*) AS record_count
FROM `zsjos_lead`
WHERE `deleted`=b'0'
GROUP BY `tenant_id`, `person_id`
HAVING COUNT(*) > 1;

SELECT 'duplicate_active_opportunities_after_lead_normalization' AS check_name,
       o.`tenant_id`, COALESCE(l.`person_id`, o.`person_id`) AS person_id, COUNT(*) AS record_count
FROM `zsjos_opportunity` o
LEFT JOIN `zsjos_lead` l
  ON l.`tenant_id`=o.`tenant_id` AND l.`id`=o.`lead_id` AND l.`deleted`=b'0'
WHERE o.`deleted`=b'0'
GROUP BY o.`tenant_id`, COALESCE(l.`person_id`, o.`person_id`)
HAVING COUNT(*) > 1;

SELECT 'lead_opportunity_relation_mismatch' AS check_name,
       l.`tenant_id`, l.`id` AS lead_id, o.`id` AS opportunity_id,
       l.`person_id` AS lead_person_id, o.`person_id` AS opportunity_person_id,
       l.`owner_user_id` AS lead_owner_user_id, o.`owner_user_id` AS opportunity_owner_user_id
FROM `zsjos_lead` l
JOIN `zsjos_opportunity` o
  ON o.`tenant_id`=l.`tenant_id` AND o.`lead_id`=l.`id` AND o.`deleted`=b'0'
WHERE l.`deleted`=b'0'
  AND (o.`person_id`<>l.`person_id` OR o.`owner_user_id`<>l.`owner_user_id`);

SELECT 'v037_schema_and_status' AS check_name,
       IF(EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V037')
          AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
               AND table_name IN ('zsjos_lead','zsjos_opportunity') AND column_name='active_person_id')=2
          AND NOT EXISTS (SELECT 1 FROM `zsjos_lead` WHERE `deleted`=b'0' AND `status`='converted'),
          'PASS', 'FAIL') AS result;
