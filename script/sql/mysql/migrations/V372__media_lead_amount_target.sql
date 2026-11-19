-- UTF-8. New independent media contribution targets. Requires Core V371 and media target table.
-- Scope: add two columns, retain all targets and grants; no business target values are invented.
-- Repeatable after partial DDL; success markers follow verified columns. DDL cannot roll back.
-- Rollback application first, retain columns and data. Do not rewrite deployed V283 checksums.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v372_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v372_apply()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V371') OR
    NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V371') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V372 requires Core V371';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_lead_target' AND column_name='target_amount') THEN
  ALTER TABLE zsjos_media_lead_target ADD COLUMN target_amount decimal(14,2) NULL COMMENT '月度业绩贡献目标';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_lead_target' AND column_name='amount_manual') THEN
  ALTER TABLE zsjos_media_lead_target ADD COLUMN amount_manual bit(1) NOT NULL DEFAULT b'0' COMMENT '业绩目标人工覆盖';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_lead_target' AND
    (column_name='target_amount' AND column_type='decimal(14,2)' AND is_nullable='YES' OR column_name='amount_manual' AND column_type='bit(1)' AND is_nullable='NO')) <> 2 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V372 target column mismatch';
 END IF;
 START TRANSACTION;
 INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V372','新媒体独立业绩目标',SHA2('V372__media_lead_amount_target.sql',256) WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V372');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V372','新媒体独立业绩目标',SHA2('V372__media_lead_amount_target.sql',256),'baseline' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V372');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v372_apply();
DROP PROCEDURE zsjos_v372_apply;
