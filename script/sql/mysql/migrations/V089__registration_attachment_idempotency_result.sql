-- V089: bind registration attachment upload replays to their exact persisted result.
-- Dependencies/order: apply after V088; requires zsjos_registration_command.
-- Data scope: additive nullable column only. Historical commands remain valid for their
-- original behavior, but a historical attachment replay without an exact result is rejected.
-- Repeatability: guarded ALTER TABLE and version insert; safe to rerun.
-- Recovery: forward-only; retain the nullable result reference on application rollback.

SET @zsjos_v089_has_result_attachment_id := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'zsjos_registration_command'
    AND column_name = 'result_attachment_id'
);
SET @zsjos_v089_sql := IF(@zsjos_v089_has_result_attachment_id = 0,
  'ALTER TABLE `zsjos_registration_command` ADD COLUMN `result_attachment_id` bigint DEFAULT NULL COMMENT ''上传附件命令的精确结果附件编号'' AFTER `operator_user_id`',
  'SELECT 1');
PREPARE zsjos_v089_stmt FROM @zsjos_v089_sql;
EXECUTE zsjos_v089_stmt;
DEALLOCATE PREPARE zsjos_v089_stmt;

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
SELECT 'V089', 'registration attachment idempotency result',
       'V089__registration_attachment_idempotency_result.sql'
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version` = 'V089');
