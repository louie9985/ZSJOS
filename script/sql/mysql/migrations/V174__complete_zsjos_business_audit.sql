-- V174 complete ZSJOS business audit metadata.
-- Scope: additive metadata columns and indexes on zsjos_business_audit_log only.
-- Prerequisite: V050 table baseline and V173 migration order.
-- Repeatability: every additive DDL is guarded through information_schema.
-- Rollback: forward-only; retain audit history and let older applications ignore new columns.

DROP PROCEDURE IF EXISTS `zsjos_v174_add_column`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v174_add_column`(IN column_name_value varchar(64), IN column_ddl text)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'zsjos_business_audit_log'
      AND column_name = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `zsjos_business_audit_log` ADD COLUMN ', column_ddl);
    PREPARE statement_handle FROM @ddl;
    EXECUTE statement_handle;
    DEALLOCATE PREPARE statement_handle;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v174_add_column`('source_type', '`source_type` varchar(32) NOT NULL DEFAULT ''EXPLICIT'' COMMENT ''操作来源'' AFTER `source_ip`');
CALL `zsjos_v174_add_column`('trace_id', '`trace_id` varchar(64) DEFAULT NULL COMMENT ''链路追踪编号'' AFTER `source_type`');
CALL `zsjos_v174_add_column`('request_method', '`request_method` varchar(16) DEFAULT NULL COMMENT ''请求方法'' AFTER `trace_id`');
CALL `zsjos_v174_add_column`('request_path', '`request_path` varchar(500) DEFAULT NULL COMMENT ''请求路径，不含查询参数'' AFTER `request_method`');
CALL `zsjos_v174_add_column`('result_status', '`result_status` varchar(16) NOT NULL DEFAULT ''SUCCESS'' COMMENT ''STARTED/SUCCESS/FAILURE'' AFTER `request_path`');
CALL `zsjos_v174_add_column`('result_code', '`result_code` int DEFAULT NULL COMMENT ''稳定业务或 HTTP 结果码'' AFTER `result_status`');
CALL `zsjos_v174_add_column`('result_message', '`result_message` varchar(500) DEFAULT NULL COMMENT ''脱敏且截断的结果摘要'' AFTER `result_code`');
CALL `zsjos_v174_add_column`('finished_at', '`finished_at` datetime DEFAULT NULL COMMENT ''操作完成时间'' AFTER `occurred_at`');
CALL `zsjos_v174_add_column`('duration_ms', '`duration_ms` bigint DEFAULT NULL COMMENT ''操作耗时毫秒'' AFTER `finished_at`');
DROP PROCEDURE `zsjos_v174_add_column`;

ALTER TABLE `zsjos_business_audit_log`
  MODIFY COLUMN `target_id` varchar(100) DEFAULT NULL COMMENT '公开业务编号；不可安全解析时为空';

DROP PROCEDURE IF EXISTS `zsjos_v174_add_index`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v174_add_index`(IN index_name_value varchar(64), IN index_ddl text)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'zsjos_business_audit_log'
      AND index_name = index_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `zsjos_business_audit_log` ADD INDEX ', index_ddl);
    PREPARE statement_handle FROM @ddl;
    EXECUTE statement_handle;
    DEALLOCATE PREPARE statement_handle;
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v174_add_index`('idx_result_time', '`idx_result_time` (`tenant_id`,`result_status`,`occurred_at`)');
CALL `zsjos_v174_add_index`('idx_operator_time', '`idx_operator_time` (`tenant_id`,`operator_user_id`,`occurred_at`)');
DROP PROCEDURE `zsjos_v174_add_index`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V174','Complete ZSJOS business audit','V174__complete_zsjos_business_audit.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V174','Complete ZSJOS business audit',SHA2('V174__complete_zsjos_business_audit.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
