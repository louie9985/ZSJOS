-- Normalizes active director interview templates by field key and retires the six-dimension field.
-- Depends on V130. Published history and service-relation snapshots remain immutable.
-- Repeatable: a named lock serializes non-transactional schema preparation; the following transaction
-- protects dictionary/template changes and the schema-version marker.

DELIMITER $$
DROP PROCEDURE IF EXISTS `zsjos_apply_v133`$$
CREATE PROCEDURE `zsjos_apply_v133`()
BEGIN
  DECLARE lock_acquired INT DEFAULT 0;
  DECLARE rows_done BOOLEAN DEFAULT FALSE;
  DECLARE current_version_id BIGINT;
  DECLARE current_template_id BIGINT;
  DECLARE current_tenant_id BIGINT;
  DECLARE current_version_status VARCHAR(24);
  DECLARE current_is_published BOOLEAN;
  DECLARE source_fields JSON;
  DECLARE transformed_fields JSON;
  DECLARE field_index INT;
  DECLARE field_key VARCHAR(100);
  DECLARE target_group VARCHAR(100);
  DECLARE target_version_no INT;
  DECLARE new_version_id BIGINT;
  DECLARE locked_published_version_id BIGINT;
  DECLARE template_cursor CURSOR FOR
    SELECT v.id,t.id,t.tenant_id,v.status,v.id=t.published_version_id,v.fields_json
      FROM zsjos_director_form_template t
      JOIN zsjos_director_form_template_version v
        ON v.template_id=t.id AND v.tenant_id=t.tenant_id AND v.deleted=b'0'
     WHERE t.scene='director_interview' AND t.deleted=b'0'
       AND (v.id=t.published_version_id AND v.status='published' OR v.status='draft')
       AND NOT (v.creator<=>'V133')
     ORDER BY t.id,v.version_no;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET rows_done = TRUE;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    IF lock_acquired = 1 THEN
      DO RELEASE_LOCK(CONCAT(DATABASE(), ':V133-director-interview-form-presentation'));
    END IF;
    RESIGNAL;
  END;

  SELECT GET_LOCK(CONCAT(DATABASE(), ':V133-director-interview-form-presentation'), 30) INTO lock_acquired;
  IF lock_acquired <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unable to acquire V133 migration lock';
  END IF;

  SET @v133_schema = DATABASE();
  SET @v133_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v133_schema
    AND table_name='zsjos_service_relation' AND column_name='director_precheck_draft_version')=0,
    'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_precheck_draft_version` int NOT NULL DEFAULT 0 COMMENT ''编导资料预审草稿版本''',
    'SELECT 1');
  PREPARE v133_stmt FROM @v133_sql;
  EXECUTE v133_stmt;
  DEALLOCATE PREPARE v133_stmt;
  SET @v133_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v133_schema
    AND table_name='zsjos_service_relation' AND column_name='director_interview_draft_version')=0,
    'ALTER TABLE `zsjos_service_relation` ADD COLUMN `director_interview_draft_version` int NOT NULL DEFAULT 0 COMMENT ''编导学员采访草稿版本''',
    'SELECT 1');
  PREPARE v133_stmt FROM @v133_sql;
  EXECUTE v133_stmt;
  DEALLOCATE PREPARE v133_stmt;

  START TRANSACTION;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V133') THEN
    INSERT INTO system_dict_data
      (sort,label,value,dict_type,status,color_type,creator,create_time,updater,update_time,deleted)
    SELECT 30,'暂不清楚','unknown','zsjos_certificate_practice',0,'default','V133',NOW(),'V133',NOW(),b'0'
    WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_certificate_practice' AND value='unknown' AND deleted=b'0');

    INSERT INTO system_dict_data
      (sort,label,value,dict_type,status,color_type,creator,create_time,updater,update_time,deleted)
    SELECT 30,'会一点','partial','zsjos_video_skill',0,'default','V133',NOW(),'V133',NOW(),b'0'
    WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='zsjos_video_skill' AND value='partial' AND deleted=b'0');

    OPEN template_cursor;
    template_loop: LOOP
      FETCH template_cursor INTO current_version_id,current_template_id,current_tenant_id,current_version_status,current_is_published,source_fields;
      IF rows_done THEN LEAVE template_loop; END IF;
      SET transformed_fields = source_fields;
      SET field_index = 0;
      WHILE field_index < JSON_LENGTH(transformed_fields) DO
        SET field_key = JSON_UNQUOTE(JSON_EXTRACT(transformed_fields, CONCAT('$[', field_index, '].key')));
        IF field_key = 'sixDimensionCommunicated' THEN
          SET transformed_fields = JSON_REMOVE(transformed_fields, CONCAT('$[', field_index, ']'));
        ELSE
          SET target_group = CASE
            WHEN field_key IN ('certificates','certificatePractice','examPreparation') THEN '证书与备考'
            WHEN field_key IN ('age','gender','region','currentOccupation','workTime','workExperience','familyMembers','hobbies') THEN '基本信息'
            WHEN field_key IN ('videoEditing','videoShooting','liveExperience','shootingEquipment','equipmentModel') THEN '自媒体运营基础能力'
            WHEN field_key IN ('mediaTime','continuousTime','appearanceWillingness','purchaseMotivations','deliveryRisks') THEN '时间与出镜'
            ELSE NULL
          END;
          IF target_group IS NOT NULL THEN
            SET transformed_fields = JSON_SET(transformed_fields, CONCAT('$[', field_index, '].group'), target_group);
          END IF;
          SET field_index = field_index + 1;
        END IF;
      END WHILE;

      IF CAST(transformed_fields AS CHAR) <> CAST(source_fields AS CHAR) THEN
        IF current_is_published THEN
          SELECT t.published_version_id INTO locked_published_version_id
            FROM zsjos_director_form_template t
           WHERE t.id=current_template_id AND t.tenant_id=current_tenant_id AND t.deleted=b'0'
           FOR UPDATE;
          IF NOT (locked_published_version_id <=> current_version_id) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Director interview template changed during V133';
          END IF;
          SELECT COALESCE(MAX(v.version_no),0)+1 INTO target_version_no
            FROM zsjos_director_form_template_version v
           WHERE v.template_id=current_template_id AND v.tenant_id=current_tenant_id AND v.deleted=b'0';
          INSERT INTO zsjos_director_form_template_version
            (template_id,version_no,status,fields_json,published_at,version,creator,create_time,updater,update_time,deleted,tenant_id)
          VALUES (current_template_id,target_version_no,'published',transformed_fields,NOW(),0,'V133',NOW(),'V133',NOW(),b'0',current_tenant_id);
          SET new_version_id = LAST_INSERT_ID();
          UPDATE zsjos_director_form_template_version
             SET status='archived',updater='V133',update_time=NOW()
           WHERE id=current_version_id AND tenant_id=current_tenant_id AND status='published' AND deleted=b'0';
          UPDATE zsjos_director_form_template
             SET published_version_id=new_version_id,version=version+1,updater='V133',update_time=NOW()
           WHERE id=current_template_id AND tenant_id=current_tenant_id
             AND published_version_id=current_version_id AND deleted=b'0';
          IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unable to replace director interview published version';
          END IF;
        ELSEIF current_version_status = 'draft' THEN
          UPDATE zsjos_director_form_template_version
             SET fields_json=transformed_fields,version=version+1,updater='V133',update_time=NOW()
           WHERE id=current_version_id AND tenant_id=current_tenant_id AND status='draft' AND deleted=b'0';
        END IF;
      END IF;
    END LOOP;
    CLOSE template_cursor;

    INSERT INTO zsjos_schema_version (version,description,checksum,installed_at)
    VALUES ('V133','director interview form presentation',SHA2('V133__director_interview_form_presentation.sql',256),NOW());
  END IF;
  COMMIT;
  DO RELEASE_LOCK(CONCAT(DATABASE(), ':V133-director-interview-form-presentation'));
END$$

CALL `zsjos_apply_v133`()$$
DROP PROCEDURE IF EXISTS `zsjos_apply_v133`$$
DELIMITER ;
