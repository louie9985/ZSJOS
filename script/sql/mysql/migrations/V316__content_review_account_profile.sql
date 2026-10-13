-- UTF-8. V316: enrich missing approval profile fields from CURRENT account data.
-- Requires Core V315 and existing account/profile and review JSON columns. Tenant 1 only,
-- active (not logically deleted) batches in every status and active associated accounts.
-- Never overwrite nonempty historical values, workflow state, identities or permissions.
-- Current-source provenance is stored per filled field; this is not historical reconstruction.
-- Transactional DML + both ledgers; repeat is missing-only. No schema changes.
-- Back up batches and ledgers before apply; rollback requires reviewed restoration of JSON
-- before-images, not resetting running workflow state. No automatic destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v316_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v316_apply()
BEGIN
 DECLARE finished BOOLEAN DEFAULT FALSE;
 DECLARE batch_id BIGINT;
 DECLARE primary_account BIGINT;
 DECLARE student_id BIGINT;
 DECLARE account_ids JSON;
 DECLARE original_context JSON;
 DECLARE context_value JSON;
 DECLARE accounts JSON;
 DECLARE profile JSON;
 DECLARE source_value JSON;
 DECLARE detail_values JSON;
 DECLARE detail_snapshots JSON;
 DECLARE field_names JSON DEFAULT JSON_ARRAY('productGoal','productFormLabel','publishFrequency','bottleneckLabel');
 DECLARE source_names JSON DEFAULT JSON_ARRAY('product_goal','work_format','publish_rhythm','bottleneck');
 DECLARE field_name VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 DECLARE source_name VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 DECLARE candidate LONGTEXT;
 DECLARE existing_value LONGTEXT;
 DECLARE account_id BIGINT;
 DECLARE i INT;
 DECLARE k INT;
 DECLARE changed BOOLEAN;
 DECLARE rows_to_fill CURSOR FOR
  SELECT id,account_id,student_person_id,account_ids_json,context_snapshot_json
  FROM zsjos_content_review_batch WHERE tenant_id=1 AND deleted=0 ORDER BY id FOR UPDATE;
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V315')
 OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V315') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V316 requires Core V315';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB'
 AND table_name IN ('zsjos_content_review_batch','zsjos_media_account','zsjos_schema_version','zsjos_module_schema_version'))<>4 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V316 requires transactional source, batches and ledgers';
 END IF;
 START TRANSACTION;
 OPEN rows_to_fill;
 batch_loop: LOOP
  FETCH rows_to_fill INTO batch_id,primary_account,student_id,account_ids,original_context;
  IF finished THEN LEAVE batch_loop; END IF;
  IF original_context IS NOT NULL AND JSON_TYPE(original_context)<>'OBJECT' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V316 invalid approval context';
  END IF;
  SET context_value=COALESCE(original_context,JSON_OBJECT());
  SET accounts=JSON_EXTRACT(context_value,'$.accountSnapshots');
  IF accounts IS NOT NULL AND JSON_TYPE(accounts) NOT IN ('ARRAY','NULL') THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V316 invalid account snapshot array';
  END IF;
  IF accounts IS NULL OR JSON_TYPE(accounts)='NULL' OR JSON_LENGTH(accounts)=0 THEN
   -- Legacy singleton context: retain its original identity fields.
   SET profile=JSON_EXTRACT(context_value,'$.account');
   IF profile IS NULL OR JSON_TYPE(profile)<>'OBJECT' THEN SET profile=JSON_OBJECT('id',primary_account); END IF;
   SET accounts=JSON_ARRAY(profile);
  END IF;
  SET i=0;
  SET changed=FALSE;
  WHILE i<JSON_LENGTH(accounts) DO
   SET profile=JSON_EXTRACT(accounts,CONCAT('$[',i,']'));
   SET account_id=CAST(JSON_UNQUOTE(JSON_EXTRACT(profile,'$.id')) AS UNSIGNED);
   SET source_value=NULL;
   IF account_id=primary_account OR JSON_CONTAINS(COALESCE(account_ids,JSON_ARRAY()),CAST(account_id AS JSON)) THEN
    SET source_value=(SELECT JSON_OBJECT('values',a.detail_values_json,'snapshots',a.detail_snapshot_json,
     'bottleneck',a.primary_problem_code_label_snapshot)
     FROM zsjos_media_account a WHERE a.id=account_id AND a.tenant_id=1 AND a.deleted=0
     AND (student_id IS NULL OR a.student_person_id=student_id));
   END IF;
   IF source_value IS NOT NULL THEN
    SET detail_values=JSON_EXTRACT(source_value,'$.values');
    SET detail_snapshots=JSON_EXTRACT(source_value,'$.snapshots');
    SET k=0;
    WHILE k<4 DO
     SET field_name=JSON_UNQUOTE(JSON_EXTRACT(field_names,CONCAT('$[',k,']')));
     SET source_name=JSON_UNQUOTE(JSON_EXTRACT(source_names,CONCAT('$[',k,']')));
     SET existing_value=JSON_UNQUOTE(JSON_EXTRACT(profile,CONCAT('$.',field_name)));
     IF existing_value IS NULL OR TRIM(existing_value)='' OR JSON_TYPE(JSON_EXTRACT(profile,CONCAT('$.',field_name)))='NULL' THEN
      SET candidate=NULL;
      IF k=3 THEN
       -- Preserve legacy problem labels that the approval already displays.
       IF COALESCE(JSON_LENGTH(profile,'$.primaryProblems'),0)=0 THEN
        SET candidate=JSON_UNQUOTE(JSON_EXTRACT(source_value,'$.bottleneck'));
        IF JSON_TYPE(JSON_EXTRACT(source_value,'$.bottleneck'))='NULL' THEN SET candidate=NULL; END IF;
       END IF;
      ELSE
       SET candidate=(SELECT j.display_value FROM JSON_TABLE(
        IF(JSON_TYPE(detail_snapshots)='ARRAY',detail_snapshots,JSON_ARRAY()),'$[*]'
        COLUMNS(field_key VARCHAR(100) PATH '$.key',display_value TEXT PATH '$.displayValue')) j
        WHERE j.field_key COLLATE utf8mb4_unicode_ci=source_name AND TRIM(j.display_value)<>'' LIMIT 1);
       IF candidate IS NULL AND k<2 AND JSON_TYPE(JSON_EXTRACT(detail_values,CONCAT('$.',source_name)))='STRING' THEN
        SET candidate=JSON_UNQUOTE(JSON_EXTRACT(detail_values,CONCAT('$.',source_name)));
       END IF;
      END IF;
      IF candidate IS NOT NULL AND TRIM(candidate)<>'' THEN
       SET profile=JSON_SET(profile,CONCAT('$.',field_name),TRIM(candidate));
       IF JSON_TYPE(JSON_EXTRACT(profile,'$.profileBackfill')) IS NULL THEN
        SET profile=JSON_SET(profile,'$.profileBackfill',JSON_OBJECT());
       END IF;
       SET profile=JSON_SET(profile,CONCAT('$.profileBackfill.',field_name),
        JSON_OBJECT('migration','V316','source','CURRENT_ACCOUNT','accountId',account_id,'filledAt',NOW()));
       SET changed=TRUE;
      END IF;
     END IF;
     SET k=k+1;
    END WHILE;
    SET accounts=JSON_SET(accounts,CONCAT('$[',i,']'),profile);
    IF JSON_EXTRACT(context_value,'$.account.id')=JSON_EXTRACT(profile,'$.id') THEN
     -- Keep the legacy account projection consistent without replacing its other fields.
     SET k=0;
     WHILE k<4 DO
      SET field_name=JSON_UNQUOTE(JSON_EXTRACT(field_names,CONCAT('$[',k,']')));
      SET existing_value=JSON_UNQUOTE(JSON_EXTRACT(context_value,CONCAT('$.account.',field_name)));
      IF (existing_value IS NULL OR TRIM(existing_value)='' OR JSON_TYPE(JSON_EXTRACT(context_value,CONCAT('$.account.',field_name)))='NULL')
      AND JSON_EXTRACT(profile,CONCAT('$.profileBackfill.',field_name)) IS NOT NULL THEN
       SET context_value=JSON_SET(context_value,CONCAT('$.account.',field_name),JSON_EXTRACT(profile,CONCAT('$.',field_name)));
      END IF;
      SET k=k+1;
     END WHILE;
    END IF;
   END IF;
   SET i=i+1;
  END WHILE;
  IF changed THEN
   SET context_value=JSON_SET(context_value,'$.accountSnapshots',accounts);
   UPDATE zsjos_content_review_batch SET context_snapshot_json=context_value,updater='V316',version=version+1
    WHERE id=batch_id AND tenant_id=1 AND deleted=0;
   IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V316 batch update postcondition failed'; END IF;
  END IF;
 END LOOP;
 CLOSE rows_to_fill;
 INSERT INTO zsjos_schema_version(version,description,checksum)
 SELECT 'V316','Content review account profile enrichment',SHA2('V316__content_review_account_profile.sql',256)
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V316');
 INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
 SELECT 'core','V316','Content review account profile enrichment',SHA2('V316__content_review_account_profile.sql',256),'baseline'
 WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V316');
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_v316_apply();
DROP PROCEDURE zsjos_v316_apply;
