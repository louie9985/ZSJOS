-- UTF-8. Development field configuration correction; prerequisite V209. No schema changes.
-- Scope: delivery_goals ownership in live published configs and drafts only.
-- Preserves existing business values. No conversion, deletion, grants or schema change.
-- Published predecessors remain archived; drafts retain their version_no and stale-draft protections.
-- Repeatable. Run with configuration editing paused. Recovery: republish predecessor via config API.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_fix_account_diagnosis_fields;
DELIMITER $$
CREATE PROCEDURE zsjos_fix_account_diagnosis_fields()
BEGIN
  DECLARE finished INT DEFAULT 0;
  DECLARE config_id BIGINT;
  DECLARE config_tenant BIGINT;
  DECLARE config_status VARCHAR(32);
  DECLARE next_version INT;
  DECLARE fields_before JSON;
  DECLARE fields_after JSON;
  DECLARE field_path TEXT;
  DECLARE configs CURSOR FOR
    SELECT id,tenant_id,status,fields_json FROM zsjos_media_account_field_config
    WHERE status IN ('published','draft') AND deleted=b'0' ORDER BY tenant_id,id FOR UPDATE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  OPEN configs;
  config_loop: LOOP
    FETCH configs INTO config_id,config_tenant,config_status,fields_before;
    IF finished THEN LEAVE config_loop; END IF;
    SET fields_after=fields_before;
    SET field_path=REPLACE(JSON_UNQUOTE(JSON_SEARCH(fields_after,'one','delivery_goals',NULL,'$[*].key')),'.key','');
    IF field_path IS NOT NULL THEN
      SET fields_after=JSON_SET(fields_after,CONCAT(field_path,'.ownerType'),'AUTO',CONCAT(field_path,'.sourceType'),'ACCOUNT',CONCAT(field_path,'.requiredForComplete'),CAST('false' AS JSON));
    END IF;
    IF NOT (fields_after <=> fields_before) THEN
      IF config_status='published' THEN
        SELECT COALESCE(MAX(version_no),0)+1 INTO next_version
          FROM zsjos_media_account_field_config WHERE tenant_id=config_tenant;
        UPDATE zsjos_media_account_field_config SET status='archived',version=version+1 WHERE id=config_id;
        INSERT INTO zsjos_media_account_field_config
          (version_no,status,fields_json,published_at,version,tenant_id,creator,updater)
        VALUES(next_version,'published',fields_after,NOW(),0,config_tenant,'','');
      ELSE
        UPDATE zsjos_media_account_field_config SET fields_json=fields_after,version=version+1 WHERE id=config_id;
      END IF;
    END IF;
  END LOOP;
  CLOSE configs;
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_fix_account_diagnosis_fields();
DROP PROCEDURE IF EXISTS zsjos_fix_account_diagnosis_fields;
