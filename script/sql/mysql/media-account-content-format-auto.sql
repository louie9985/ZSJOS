-- UTF-8. Unreleased configuration correction: content_format is projected from the applied positioning snapshot.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_fix_content_format_auto;
DELIMITER $$
CREATE PROCEDURE zsjos_fix_content_format_auto()
BEGIN
 DECLARE done INT DEFAULT 0; DECLARE id0 BIGINT; DECLARE tenant0 BIGINT; DECLARE status0 VARCHAR(32); DECLARE ver INT; DECLARE fields JSON; DECLARE path0 TEXT;
 DECLARE c CURSOR FOR SELECT id,tenant_id,status,fields_json FROM zsjos_media_account_field_config WHERE deleted=b'0' AND status IN ('published','draft') ORDER BY tenant_id,id FOR UPDATE;
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1; DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION; OPEN c;
 loop0: LOOP FETCH c INTO id0,tenant0,status0,fields; IF done THEN LEAVE loop0; END IF;
  SET path0=REPLACE(JSON_UNQUOTE(JSON_SEARCH(fields,'one','content_format',NULL,'$[*].key')),'.key','');
  IF path0 IS NOT NULL AND (JSON_UNQUOTE(JSON_EXTRACT(fields,CONCAT(path0,'.ownerType'))) <> 'AUTO' OR JSON_UNQUOTE(JSON_EXTRACT(fields,CONCAT(path0,'.type'))) <> 'text') THEN
   SET fields=JSON_SET(fields,CONCAT(path0,'.ownerType'),'AUTO',CONCAT(path0,'.type'),'text',CONCAT(path0,'.dictType'),NULL);
   IF status0='published' THEN
    SELECT COALESCE(MAX(version_no),0)+1 INTO ver FROM zsjos_media_account_field_config WHERE tenant_id=tenant0;
    UPDATE zsjos_media_account_field_config SET status='archived',version=version+1 WHERE id=id0;
    INSERT INTO zsjos_media_account_field_config(version_no,status,fields_json,published_at,version,tenant_id,creator,updater) VALUES(ver,'published',fields,NOW(),0,tenant0,'','');
   ELSE UPDATE zsjos_media_account_field_config SET fields_json=fields,version=version+1 WHERE id=id0; END IF;
  END IF;
 END LOOP; CLOSE c; COMMIT;
END$$
DELIMITER ;
CALL zsjos_fix_content_format_auto();
DROP PROCEDURE IF EXISTS zsjos_fix_content_format_auto;
