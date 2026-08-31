-- V054: add tenant-daily Lead business numbers. Apply after V053.
-- Data scope: every existing zsjos_lead row receives one deterministic number ordered by
-- tenant, submitted Beijing-local date, submitted_at, and id. No rows are deleted.
-- Repeatability: guarded DDL, null-only backfill, counter upsert, and version upserts support reruns.
-- Rollback limitation: assigned numbers are durable business identifiers and must not be removed.

SET @ddl = (SELECT IF(EXISTS (
  SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='lead_no'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD COLUMN `lead_no` varchar(32) DEFAULT NULL COMMENT ''客资业务编号'' AFTER `id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zsjos_lead_no_daily_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `sequence_date` date NOT NULL COMMENT '北京时间业务日期',
  `current_value` bigint NOT NULL COMMENT '当日最后已分配循环序号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_sequence_date` (`tenant_id`,`sequence_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 客资业务编号日序';

DROP TEMPORARY TABLE IF EXISTS `tmp_v054_lead_no`;
CREATE TEMPORARY TABLE `tmp_v054_lead_no` (
  `id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `sequence_date` date NOT NULL,
  `sequence_value` bigint NOT NULL,
  `lead_no` varchar(32) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

INSERT INTO `tmp_v054_lead_no` (`id`,`tenant_id`,`sequence_date`,`sequence_value`,`lead_no`)
SELECT ranked.id, ranked.tenant_id, ranked.sequence_date, ranked.sequence_value,
       CONCAT('KZ', DATE_FORMAT(ranked.submitted_at, '%Y%m%d%H%i%s'),
              LPAD(ranked.sequence_value, 4, '0'))
FROM (
  SELECT `id`, `tenant_id`, DATE(`submitted_at`) AS sequence_date, `submitted_at`,
         MOD(ROW_NUMBER() OVER (
           PARTITION BY `tenant_id`, DATE(`submitted_at`)
           ORDER BY `submitted_at`, `id`
         ) - 1, 9999) + 1 AS sequence_value
  FROM `zsjos_lead`
) ranked;

-- Avoid `generated` as an alias because it is reserved by MySQL 8.4.
UPDATE `zsjos_lead` lead_row
JOIN `tmp_v054_lead_no` generated_row ON generated_row.id=lead_row.id
SET lead_row.lead_no=generated_row.lead_no
WHERE lead_row.lead_no IS NULL;

INSERT INTO `zsjos_lead_no_daily_counter`
(`sequence_date`,`current_value`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT `sequence_date`, MOD(COUNT(*) - 1, 9999) + 1, 'migration-V054', NOW(),
       'migration-V054', NOW(), b'0', `tenant_id`
FROM `tmp_v054_lead_no`
GROUP BY `tenant_id`,`sequence_date`
ON DUPLICATE KEY UPDATE
  `current_value`=VALUES(`current_value`),
  `updater`='migration-V054', `update_time`=NOW();

DROP TEMPORARY TABLE `tmp_v054_lead_no`;

SET @ddl = (SELECT IF((SELECT is_nullable FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='lead_no')='NO',
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` MODIFY COLUMN `lead_no` varchar(32) NOT NULL COMMENT ''客资业务编号'''));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS (
  SELECT 1 FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND index_name='uk_tenant_lead_no'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead` ADD UNIQUE KEY `uk_tenant_lead_no` (`tenant_id`,`lead_no`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V054','Tenant-daily Lead business numbers','lead-business-number-v2')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V054','Tenant-daily Lead business numbers',SHA2('lead-business-number-v2',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`),
  `release_version`=VALUES(`release_version`),`installed_at`=VALUES(`installed_at`);
