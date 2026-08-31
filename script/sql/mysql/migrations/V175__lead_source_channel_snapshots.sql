-- V175 persist source-channel labels at Lead selection time.
-- Scope: additive nullable snapshot columns only; existing rows are intentionally not backfilled
-- because the historical label at their selection time cannot be reconstructed reliably.
-- Prerequisite: the Lead and duplicate-review tables from the core baseline.
-- Repeatability: information_schema guards make each additive change safe to rerun.
-- Rollback: forward-only; older applications may ignore the nullable columns.

DROP PROCEDURE IF EXISTS `zsjos_v175_add_column`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v175_add_column`(
  IN table_name_value varchar(64), IN column_name_value varchar(64), IN column_ddl text)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = table_name_value
      AND column_name = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN ', column_ddl);
    PREPARE statement_handle FROM @ddl;
    EXECUTE statement_handle;
    DEALLOCATE PREPARE statement_handle;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v175_add_column`('zsjos_lead', 'source_channel_label_snapshot',
  '`source_channel_label_snapshot` varchar(128) DEFAULT NULL COMMENT ''来源渠道选择时标签快照'' AFTER `source_channel_id`');
CALL `zsjos_v175_add_column`('zsjos_lead_duplicate_review', 'source_channel_label_snapshot',
  '`source_channel_label_snapshot` varchar(128) DEFAULT NULL COMMENT ''提交时来源渠道标签快照'' AFTER `lead_category_label_snapshot`');

DROP PROCEDURE `zsjos_v175_add_column`;

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`, `installed_at`)
VALUES ('V175', 'Lead source channel snapshots', SHA2('V175__lead_source_channel_snapshots.sql', 256), NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `checksum` = VALUES(`checksum`);
