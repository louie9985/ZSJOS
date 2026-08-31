-- V171: Lead duplicate-rule result contract.
-- Depends on V170 in the integrated development baseline.
-- Scope: add nullable duplicate rule/result/fingerprint columns and a lookup index to duplicate-review records.
-- Existing review rows are not backfilled because their historical duplicate flag and fingerprint cannot be reconstructed safely.
-- Repeatable: guarded by information_schema for columns/indexes and records both schema-version registries.
-- Rollback limitation: keep the additive columns and index while older application versions ignore them.

DROP PROCEDURE IF EXISTS `zsjos_v171_add_column`;
DROP PROCEDURE IF EXISTS `zsjos_v171_add_index`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v171_add_column`(IN column_name_arg varchar(64), IN ddl_arg text)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema=DATABASE() AND table_name='zsjos_lead_duplicate_review')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema=DATABASE()
                        AND table_name='zsjos_lead_duplicate_review'
                        AND column_name=column_name_arg) THEN
    SET @zsjos_v171_ddl = ddl_arg;
    PREPARE zsjos_v171_stmt FROM @zsjos_v171_ddl;
    EXECUTE zsjos_v171_stmt;
    DEALLOCATE PREPARE zsjos_v171_stmt;
  END IF;
END$$
CREATE PROCEDURE `zsjos_v171_add_index`(IN index_name_arg varchar(64), IN ddl_arg text)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema=DATABASE() AND table_name='zsjos_lead_duplicate_review')
     AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
                      WHERE table_schema=DATABASE()
                        AND table_name='zsjos_lead_duplicate_review'
                        AND index_name=index_name_arg) THEN
    SET @zsjos_v171_ddl = ddl_arg;
    PREPARE zsjos_v171_stmt FROM @zsjos_v171_ddl;
    EXECUTE zsjos_v171_stmt;
    DEALLOCATE PREPARE zsjos_v171_stmt;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v171_add_column`('duplicate_flag',
  'ALTER TABLE `zsjos_lead_duplicate_review` ADD COLUMN `duplicate_flag` varchar(32) DEFAULT NULL COMMENT ''查重类型：none/strong_duplicate/suspected_duplicate'' AFTER `lead_category_label_snapshot`');
CALL `zsjos_v171_add_column`('duplicate_result',
  'ALTER TABLE `zsjos_lead_duplicate_review` ADD COLUMN `duplicate_result` varchar(32) DEFAULT NULL COMMENT ''查重处理结果'' AFTER `duplicate_flag`');
CALL `zsjos_v171_add_column`('primary_rule_code',
  'ALTER TABLE `zsjos_lead_duplicate_review` ADD COLUMN `primary_rule_code` varchar(64) DEFAULT NULL COMMENT ''首要命中规则编码'' AFTER `duplicate_result`');
CALL `zsjos_v171_add_column`('review_fingerprint',
  'ALTER TABLE `zsjos_lead_duplicate_review` ADD COLUMN `review_fingerprint` varchar(64) DEFAULT NULL COMMENT ''疑似重复合并指纹'' AFTER `primary_rule_code`');

CALL `zsjos_v171_add_index`('idx_tenant_duplicate_pending',
  'CREATE INDEX `idx_tenant_duplicate_pending` ON `zsjos_lead_duplicate_review` (`tenant_id`,`status`,`review_fingerprint`)');

DROP PROCEDURE IF EXISTS `zsjos_v171_add_column`;
DROP PROCEDURE IF EXISTS `zsjos_v171_add_index`;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V171','Lead duplicate rule contract',
        SHA2('V171__lead_duplicate_rule_contract.sql',256),NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
        (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V171','Lead duplicate rule contract',
        SHA2('V171__lead_duplicate_rule_contract.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
