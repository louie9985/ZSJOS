-- UTF-8. V266: one-time attribution repair for imported Partner Leads.
--
-- Prerequisites: V265; imported Leads retain the `legacy-parttimecrm-%` submission marker;
-- current Partner ownership, employee and department facts must already be synchronized.
-- Scope: only non-deleted, counted Partner Leads with that import marker and with both
-- contribution user/department snapshots absent. The current confirmed ownership is the
-- explicit import-reconciliation authority for these legacy rows.
--
-- The migration preserves Lead business state, counted_at, provider identity and ownership.
-- It does not rewrite existing daily media-screen snapshots. A re-run only records the
-- version because the null-snapshot predicate prevents a second attribution update.
-- Rollback: restore the affected snapshot columns from a scoped backup; do not infer a
-- historical owner from names or an unreviewed relationship.
SET NAMES utf8mb4;

START TRANSACTION;

UPDATE `zsjos_lead` lead_row
JOIN `zsjos_partner_ownership` ownership
  ON ownership.`tenant_id` = lead_row.`tenant_id`
 AND ownership.`partner_id` = lead_row.`provider_owner_id`
 AND ownership.`deleted` = b'0'
JOIN `system_users` employee
  ON employee.`tenant_id` = ownership.`tenant_id`
 AND employee.`id` = ownership.`employee_user_id`
 AND employee.`deleted` = b'0'
 AND employee.`status` = 0
JOIN `system_dept` department
  ON department.`tenant_id` = employee.`tenant_id`
 AND department.`id` = employee.`dept_id`
 AND department.`deleted` = b'0'
LEFT JOIN `system_users` supervisor
  ON supervisor.`tenant_id` = department.`tenant_id`
 AND supervisor.`id` = department.`leader_user_id`
 AND supervisor.`deleted` = b'0'
SET lead_row.`contribution_user_id_snapshot` = employee.`id`,
    lead_row.`contribution_user_name_snapshot` = ownership.`employee_name_snapshot`,
    lead_row.`contribution_dept_id_snapshot` = department.`id`,
    lead_row.`contribution_dept_name_snapshot` = department.`name`,
    lead_row.`contribution_supervisor_user_id_snapshot` = supervisor.`id`,
    lead_row.`contribution_supervisor_name_snapshot` = supervisor.`nickname`,
    lead_row.`updater` = 'V266',
    lead_row.`update_time` = NOW()
WHERE lead_row.`deleted` = b'0'
  AND lead_row.`provider_owner_type` = 'partner'
  AND lead_row.`submission_idempotency_key` LIKE 'legacy-parttimecrm-%'
  AND lead_row.`counted_at` IS NOT NULL
  AND lead_row.`contribution_user_id_snapshot` IS NULL
  AND lead_row.`contribution_dept_id_snapshot` IS NULL;

-- Remaining rows are intentionally excluded when their current attribution cannot be
-- established from a live ownership, enabled employee and live department.
SELECT 'V266-remaining-imported-partner-attribution' AS check_name,
       COUNT(*) AS lead_count
FROM `zsjos_lead` lead_row
WHERE lead_row.`deleted` = b'0'
  AND lead_row.`provider_owner_type` = 'partner'
  AND lead_row.`submission_idempotency_key` LIKE 'legacy-parttimecrm-%'
  AND lead_row.`counted_at` IS NOT NULL
  AND lead_row.`contribution_user_id_snapshot` IS NULL
  AND lead_row.`contribution_dept_id_snapshot` IS NULL;

COMMIT;

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`, `installed_at`)
VALUES ('V266', '回填导入兼职客资大屏归属快照', SHA2('V266__backfill_imported_partner_media_attribution.sql', 256), NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
VALUES ('core', 'V266', '回填导入兼职客资大屏归属快照', SHA2('V266__backfill_imported_partner_media_attribution.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`),
                        `release_version` = VALUES(`release_version`);
