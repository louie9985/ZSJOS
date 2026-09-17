-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V144: remove the retired new-media Student Operations domains.
-- Dependencies: apply after the rewritten development baseline.
-- Deletion scope: Student Operations role grants and menu nodes; graduation notification
-- template/rules/messages and business events; exception-ticket, cooperation-assessment,
-- and graduation-application tables. Student-contact extension data and BPM are excluded.
-- Repeatability: stable permission codes, scene codes, aggregate types, and DROP TABLE IF EXISTS.
-- Recovery: forward-only. Restore the pre-execution database backup to recover deleted data.

SET NAMES utf8mb4;
DELETE message FROM `system_notify_message` message
WHERE message.`scene_code` = 'media.graduation.result' OR message.`biz_type` = 'media-graduation';
DELETE rule_row FROM `system_notify_rule` rule_row WHERE rule_row.`scene_code` = 'media.graduation.result';
DELETE template FROM `system_notify_template` template
WHERE template.`code` = 'ZSJOS_MEDIA_GRADUATION_RESULT' OR template.`scene_code` = 'media.graduation.result';
DELETE event_row FROM `zsjos_business_event` event_row
WHERE event_row.`aggregate_type` = 'media-graduation' OR event_row.`idempotency_key` LIKE 'media-graduation:%';

DELETE menu_row FROM `system_menu` menu_row
WHERE menu_row.`permission` LIKE 'zsjos:student-ops:%' OR menu_row.`id` IN (6984, 7013, 7014, 7015, 7021);
DROP TABLE IF EXISTS `zsjos_exception_ticket`;
DROP TABLE IF EXISTS `zsjos_cooperation_assessment`;
DROP TABLE IF EXISTS `zsjos_graduation_application`;
INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`, `installed_at`)
VALUES ('V144', 'Remove new-media Student Operations domains', 'V144__remove_new_media_student_operations.sql', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
  (`module_code`, `version`, `description`, `checksum`, `release_version`, `installed_at`)
VALUES ('core', 'V144', 'Remove new-media Student Operations domains', SHA2('V144__remove_new_media_student_operations.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);
