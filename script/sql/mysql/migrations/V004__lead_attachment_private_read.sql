-- Adds stable Infra file references for private-read lead attachments.
-- Dependencies: infra_file and zsjos_lead_attachment must already exist.
-- Execution order: add the nullable reference, make the legacy URL nullable, backfill exact URL matches, add the lookup index,
-- then record V004. The application keeps legacy file_url as a fallback for unmatched historical rows.
-- Repeatability: schema changes are information_schema guarded; backfill only touches NULL references.
-- Data scope: updates only zsjos_lead_attachment.infra_file_id and file_url nullability. No files or rows are deleted.
-- Rollback limitation: forward-only; the nullable column and index may remain unused if application code rolls back.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead_attachment'
            AND column_name='infra_file_id'),
  'SELECT 1',
  'ALTER TABLE `zsjos_lead_attachment` ADD COLUMN `infra_file_id` bigint NULL COMMENT ''Infra 文件编号；通过公开 API 解析，不建立跨模块外键'' AFTER `lead_id`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead_attachment'
            AND column_name='file_url' AND is_nullable='NO'),
  'ALTER TABLE `zsjos_lead_attachment` MODIFY COLUMN `file_url` varchar(1024) NULL COMMENT ''历史 Infra 文件访问地址；新数据使用 infra_file_id''',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `zsjos_lead_attachment` attachment
SET `infra_file_id` = (
  SELECT MAX(file_record.`id`)
  FROM `infra_file` file_record
  WHERE file_record.`url` = attachment.`file_url` AND file_record.`deleted` = b'0'
)
WHERE attachment.`infra_file_id` IS NULL
  AND EXISTS (
    SELECT 1 FROM `infra_file` file_record
    WHERE file_record.`url` = attachment.`file_url` AND file_record.`deleted` = b'0'
  );

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.statistics
          WHERE table_schema=DATABASE() AND table_name='zsjos_lead_attachment'
            AND index_name='idx_tenant_infra_file'),
  'SELECT 1',
  'CREATE INDEX `idx_tenant_infra_file` ON `zsjos_lead_attachment` (`tenant_id`, `infra_file_id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V004', 'Reference Infra files for private-read lead attachments', 'lead-attachment-private-read-v1');
