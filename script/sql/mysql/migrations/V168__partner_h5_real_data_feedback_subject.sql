-- V168: Partner H5 real-data feedback subject fields.
-- Depends on V149 feedback management and follows V167 in the baseline chain.
-- Scope: add typed feedback/work-order subject columns and Partner feedback indexes only.
-- Existing rows keep ADMIN defaults; no feedback, work orders, accounts, messages or business rows are deleted.
-- Repeatable: guarded by information_schema for columns/indexes and records both schema-version registries.
-- Rollback limitation: keep the additive columns while older application versions ignore them, or hide Partner H5 feedback in a later migration.

DROP PROCEDURE IF EXISTS `zsjos_v168_add_column`;
DROP PROCEDURE IF EXISTS `zsjos_v168_add_index`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v168_add_column`(IN table_name_arg varchar(64), IN column_name_arg varchar(64), IN ddl_arg text)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=table_name_arg)
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema=DATABASE() AND table_name=table_name_arg AND column_name=column_name_arg) THEN
    SET @zsjos_v168_ddl = ddl_arg;
    PREPARE zsjos_v168_stmt FROM @zsjos_v168_ddl;
    EXECUTE zsjos_v168_stmt;
    DEALLOCATE PREPARE zsjos_v168_stmt;
  END IF;
END$$
CREATE PROCEDURE `zsjos_v168_add_index`(IN table_name_arg varchar(64), IN index_name_arg varchar(64), IN ddl_arg text)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=table_name_arg)
     AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
                      WHERE table_schema=DATABASE() AND table_name=table_name_arg AND index_name=index_name_arg) THEN
    SET @zsjos_v168_ddl = ddl_arg;
    PREPARE zsjos_v168_stmt FROM @zsjos_v168_ddl;
    EXECUTE zsjos_v168_stmt;
    DEALLOCATE PREPARE zsjos_v168_stmt;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v168_add_column`('zsjos_feedback', 'submitter_subject_type',
  'ALTER TABLE `zsjos_feedback` ADD COLUMN `submitter_subject_type` varchar(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''提交主体类型'' AFTER `status`');
CALL `zsjos_v168_add_column`('zsjos_feedback', 'partner_id',
  'ALTER TABLE `zsjos_feedback` ADD COLUMN `partner_id` bigint DEFAULT NULL COMMENT ''合作方主体编号'' AFTER `submitter_name_snapshot`');
CALL `zsjos_v168_add_column`('zsjos_work_order', 'source_subject_type',
  'ALTER TABLE `zsjos_work_order` ADD COLUMN `source_subject_type` varchar(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''发起主体类型'' AFTER `assignment_mode`');
CALL `zsjos_v168_add_column`('zsjos_work_order', 'command_subject_type',
  'ALTER TABLE `zsjos_work_order` ADD COLUMN `command_subject_type` varchar(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''命令主体类型'' AFTER `idempotency_key`');
CALL `zsjos_v168_add_column`('zsjos_work_order_history', 'operator_subject_type',
  'ALTER TABLE `zsjos_work_order_history` ADD COLUMN `operator_subject_type` varchar(32) NOT NULL DEFAULT ''ADMIN'' COMMENT ''操作主体类型'' AFTER `to_status`');

CALL `zsjos_v168_add_index`('zsjos_feedback', 'idx_submitter_subject_activity',
  'CREATE INDEX `idx_submitter_subject_activity` ON `zsjos_feedback` (`tenant_id`,`submitter_subject_type`,`submitter_user_id`,`deleted`,`last_activity_at`)');
CALL `zsjos_v168_add_index`('zsjos_feedback', 'idx_feedback_partner',
  'CREATE INDEX `idx_feedback_partner` ON `zsjos_feedback` (`tenant_id`,`partner_id`,`deleted`)');

DROP PROCEDURE IF EXISTS `zsjos_v168_add_column`;
DROP PROCEDURE IF EXISTS `zsjos_v168_add_index`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V168','Partner H5 feedback subject fields',
        SHA2('V168__partner_h5_real_data_feedback_subject.sql',256),NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
        (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V168','Partner H5 feedback subject fields',
        SHA2('V168__partner_h5_real_data_feedback_subject.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
