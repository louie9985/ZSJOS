-- V065: stable inbox cursor ordering and lead activity projection.
-- Dependency/order: apply after V064 and before deploying cursor-based Workbench clients.
-- Repeatability: column and index DDL are information_schema guarded; backfill only raises activity time.
-- Data scope: every non-deleted lead receives an activity timestamp derived from persisted lead, assignment,
-- follow-up, appeal, and order facts. No business row is deleted.
-- Rollback limitation: application rollback may ignore this additive field/index; retaining them is safe.

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead' AND column_name='last_activity_at'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `last_activity_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''最近业务活动时间'' AFTER `submitted_at`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `zsjos_lead` l
SET l.`last_activity_at` = GREATEST(
  l.`submitted_at`, l.`update_time`,
  COALESCE((SELECT MAX(h.`occurred_at`) FROM `zsjos_lead_assignment_history` h
            WHERE h.`tenant_id`=l.`tenant_id` AND h.`lead_id`=l.`id` AND h.`deleted`=b'0'), l.`submitted_at`),
  COALESCE((SELECT MAX(f.`occurred_at`) FROM `zsjos_lead_follow_up_record` f
            WHERE f.`tenant_id`=l.`tenant_id` AND f.`lead_id`=l.`id` AND f.`deleted`=b'0'), l.`submitted_at`),
  COALESCE((SELECT MAX(a.`submitted_at`) FROM `zsjos_lead_appeal` a
            WHERE a.`tenant_id`=l.`tenant_id` AND a.`lead_id`=l.`id` AND a.`deleted`=b'0'), l.`submitted_at`),
  COALESCE((SELECT MAX(o.`update_time`) FROM `zsjos_order` o
            WHERE o.`tenant_id`=l.`tenant_id` AND o.`lead_id`=l.`id` AND o.`deleted`=b'0'), l.`submitted_at`)
)
WHERE l.`deleted`=b'0';

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_lead' AND index_name='idx_tenant_last_activity'), 'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD INDEX `idx_tenant_last_activity` (`tenant_id`,`last_activity_at`,`id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V065','inbox cursor activity ordering','V065__inbox_cursor_activity_ordering.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V065','inbox cursor activity ordering',SHA2('V065__inbox_cursor_activity_ordering.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
