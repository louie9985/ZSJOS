-- UTF-8. Development-baseline correction, after V209 (also sourced by V209).
-- Scope: live published configs whose cover image is UNASSIGNED. Publish a new
-- version changing only cover.ownerType to OPERATOR; archive its predecessor.
-- Preserve custom fields, required flags, drafts, old JSON, accounts and attachments.
-- Repeatability: already assigned/missing covers and deleted configs are untouched.
-- Run during a controlled config-maintenance window, before editing/publishing drafts.
-- Recovery: republish the predecessor JSON via the config API; retain new history.
-- No schema-version increment: this corrects the unreleased development baseline.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_fix_account_cover_operator;
DELIMITER $$
CREATE PROCEDURE zsjos_fix_account_cover_operator()
BEGIN
  DECLARE finished INT DEFAULT 0;
  DECLARE config_id BIGINT;
  DECLARE config_tenant BIGINT;
  DECLARE next_version INT;
  DECLARE fields_before JSON;
  DECLARE cover_path TEXT;
  DECLARE configs CURSOR FOR
    SELECT id,tenant_id,fields_json FROM zsjos_media_account_field_config
    WHERE status='published' AND deleted=b'0' ORDER BY tenant_id,id FOR UPDATE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  START TRANSACTION;
  OPEN configs;
  config_loop: LOOP
    FETCH configs INTO config_id,config_tenant,fields_before;
    IF finished THEN LEAVE config_loop; END IF;
    SET cover_path=REPLACE(JSON_UNQUOTE(JSON_SEARCH(fields_before,'one','cover',NULL,'$[*].key')),'.key','');
    IF cover_path IS NOT NULL
      AND JSON_UNQUOTE(JSON_EXTRACT(fields_before,CONCAT(cover_path,'.ownerType')))='UNASSIGNED'
      AND JSON_UNQUOTE(JSON_EXTRACT(fields_before,CONCAT(cover_path,'.type')))='image' THEN
      SELECT COALESCE(MAX(version_no),0)+1 INTO next_version
        FROM zsjos_media_account_field_config WHERE tenant_id=config_tenant;
      UPDATE zsjos_media_account_field_config SET status='archived'
        WHERE id=config_id AND status='published' AND deleted=b'0';
      INSERT INTO zsjos_media_account_field_config
        (version_no,status,fields_json,published_at,version,tenant_id,creator,updater)
      VALUES(next_version,'published',
        JSON_SET(fields_before,CONCAT(cover_path,'.ownerType'),'OPERATOR'),
        NOW(),0,config_tenant,'','');
    END IF;
  END LOOP;
  CLOSE configs;
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_fix_account_cover_operator();
DROP PROCEDURE IF EXISTS zsjos_fix_account_cover_operator;
