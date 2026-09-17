-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- UTF-8. Requires V203 and V204; does not modify any business row.
-- Scope: two authorization gaps confirmed on the V203/V204 baseline.
--   (1) The media-account create endpoint enforces the button permission `zsjos:media-account:create`
--       while its object check requires the caller to be the service relation's content director.
--       V107 granted that button only to `new_media_operator`, so no non-superadmin could ever
--       create an account: the operator holds the button but fails the object check, and the
--       director passes the object check but is rejected before the service is reached.
--       Creation is a director responsibility per the delivered account-ownership design.
--   (2) V203 created the three positioning-interview action menus without granting them to any
--       role or extending tenant packages, so every interview endpoint returned permission denied
--       for all real roles. V192/V194/V198 and V131 extend packages for the menus they add.
-- Repeatability: every statement is guarded by stable permission keys plus NOT EXISTS /
-- NOT JSON_CONTAINS, so re-running adds nothing and rewrites nothing.
-- Rollback: forward-only. Remove the role-menu grants to revoke; do not delete the menus, because
-- published positioning-interview template versions and menu ids are referenced by the application.
SET NAMES utf8mb4;

-- 3. Extend tenant packages that already expose the account page (6970) with its create action.
UPDATE `system_tenant_package` p
SET p.`menu_ids`=JSON_ARRAY_APPEND(p.`menu_ids`,'$',6971),p.`updater`='V207',p.`update_time`=NOW()
WHERE p.`deleted`=b'0' AND JSON_CONTAINS(p.`menu_ids`,'6970','$')
  AND NOT JSON_CONTAINS(p.`menu_ids`,'6971','$');

-- 4. Extend tenant packages that already expose the media-student page (7022) with the new actions,
--    resolving the menu id by permission because V203 created these rows without a fixed id.
UPDATE `system_tenant_package` p
JOIN `system_menu` m ON m.permission='zsjos:student:positioning-interview' AND m.deleted=b'0'
SET p.`menu_ids`=JSON_ARRAY_APPEND(p.`menu_ids`,'$',m.id),p.`updater`='V207',p.`update_time`=NOW()
WHERE p.`deleted`=b'0' AND JSON_CONTAINS(p.`menu_ids`,'7022','$')
  AND NOT JSON_CONTAINS(p.`menu_ids`,CAST(m.id AS CHAR),'$');

UPDATE `system_tenant_package` p
JOIN `system_menu` m ON m.permission='zsjos:student:positioning-interview-query' AND m.deleted=b'0'
SET p.`menu_ids`=JSON_ARRAY_APPEND(p.`menu_ids`,'$',m.id),p.`updater`='V207',p.`update_time`=NOW()
WHERE p.`deleted`=b'0' AND JSON_CONTAINS(p.`menu_ids`,'7022','$')
  AND NOT JSON_CONTAINS(p.`menu_ids`,CAST(m.id AS CHAR),'$');

UPDATE `system_tenant_package` p
JOIN `system_menu` m ON m.permission='zsjos:student:positioning-interview-complete' AND m.deleted=b'0'
SET p.`menu_ids`=JSON_ARRAY_APPEND(p.`menu_ids`,'$',m.id),p.`updater`='V207',p.`update_time`=NOW()
WHERE p.`deleted`=b'0' AND JSON_CONTAINS(p.`menu_ids`,'7022','$')
  AND NOT JSON_CONTAINS(p.`menu_ids`,CAST(m.id AS CHAR),'$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V207','grant account creation and positioning interview actions',SHA2('V207__grant_media_account_and_positioning_interview_actions.sql',256),NOW())
ON DUPLICATE KEY UPDATE `checksum`=VALUES(`checksum`),`description`=VALUES(`description`);
INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V207','grant account creation and positioning interview actions',SHA2('V207__grant_media_account_and_positioning_interview_actions.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `checksum`=VALUES(`checksum`),`description`=VALUES(`description`);
