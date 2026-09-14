-- UTF-8. V208: publish the corrected viral-account template as a new immutable version.
-- Dependencies/order: apply after V207.
-- Data scope: tenant-scoped viral_account schema versions and their material-type pointers only.
-- It preserves all material/content rows and immutable historical schema snapshots.
-- Upgrade rule: empty schema sets and the two known application-owned V1/V2 hashes are upgraded;
-- unknown hashes are treated as tenant customizations and remain unchanged.
-- Repeatability: an existing canonical hash is reused and no additional version is created.
-- Rollback: forward-only after new materials reference V3; relink the previous archived version if required.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_V208_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_V208_apply`()
BEGIN
  DECLARE done int DEFAULT 0;
  DECLARE v_type_id bigint;
  DECLARE v_tenant_id bigint;
  DECLARE v_current_id bigint;
  DECLARE v_source_id bigint;
  DECLARE v_target_id bigint;
  DECLARE v_source_hash varchar(64);
  DECLARE v_next_version int;
  DECLARE v_fields longtext;
  DECLARE v_canonical_hash varchar(64);
  DECLARE viral_types CURSOR FOR
    SELECT `id`, `tenant_id`, `current_schema_version_id`
    FROM `zsjos_material_type`
    WHERE `code`='viral_account' AND `deleted`=b'0'
    ORDER BY `tenant_id`, `id`;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V194')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V194') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V208 requires V194 in both schema-version registries';
  END IF;

  SET v_fields='[{"key":"account_name","label":"账号名称","required":true,"searchable":true,"section":"ACCOUNT_DETAIL","sort":10,"type":"text"},{"dictType":"zsjos_account_platform","key":"account_platform","label":"账号平台","required":true,"searchable":true,"section":"ACCOUNT_DETAIL","sort":20,"type":"dict-single"},{"key":"account_authentication","label":"账号认证","required":true,"searchable":true,"section":"ACCOUNT_DETAIL","sort":30,"type":"text"},{"key":"homepage_id","label":"主页ID","required":true,"searchable":true,"section":"ACCOUNT_DETAIL","sort":40,"type":"text"},{"key":"homepage_url","label":"主页链接","required":true,"searchable":true,"section":"ACCOUNT_DETAIL","sort":50,"type":"https-link"},{"key":"avatar_settings","label":"头像设置","required":false,"searchable":true,"section":"ACCOUNT_DETAIL","sort":60,"type":"text"},{"key":"background_settings","label":"背景设置","required":false,"searchable":true,"section":"ACCOUNT_DETAIL","sort":70,"type":"text"},{"key":"profile_bio","label":"主页引导语","required":false,"searchable":true,"section":"ACCOUNT_DETAIL","sort":80,"type":"text"},{"key":"pinned_content","label":"主页置顶内容","required":false,"searchable":true,"section":"ACCOUNT_DETAIL","sort":90,"type":"textarea"},{"key":"hottest_work_url","label":"最火作品链接","required":false,"searchable":true,"section":"ACCOUNT_DETAIL","sort":100,"type":"https-link"},{"key":"comment_strategy","label":"评论区策略","required":false,"searchable":true,"section":"ACCOUNT_DETAIL","sort":110,"type":"textarea"},{"key":"monetization_methods","label":"账号变现方式","required":false,"searchable":true,"section":"ACCOUNT_DETAIL","sort":120,"type":"textarea"},{"group":"账号设置","key":"background_strategy","label":"账号设置：背景策略","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":130,"type":"textarea"},{"group":"账号设置","key":"avatar_strategy","label":"账号设置：头像策略","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":140,"type":"textarea"},{"group":"账号设置","key":"account_name_strategy","label":"账号设置：账号名策略","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":150,"type":"textarea"},{"group":"账号设置","key":"profile_bio_strategy","label":"账号设置：主页引导语策略","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":160,"type":"textarea"},{"group":"账号设置","key":"pinned_strategy","label":"账号设置：置顶策略","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":170,"type":"textarea"},{"children":[{"key":"matrix_content","label":"矩阵内容","required":true,"searchable":true,"sort":10,"type":"text"},{"key":"proportion","label":"占比","required":true,"searchable":true,"sort":20,"type":"text"},{"key":"purpose","label":"目的","required":true,"searchable":true,"sort":30,"type":"text"},{"key":"content_analysis","label":"内容解析","required":true,"searchable":true,"sort":40,"type":"textarea"}],"group":"内容矩阵","key":"content_matrix","label":"内容矩阵","minCount":1,"required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":180,"type":"repeat-group"},{"group":"内容节奏","key":"rhythm_start","label":"内容节奏：开始","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":190,"type":"textarea"},{"group":"内容节奏","key":"rhythm_transition","label":"内容节奏：转型","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":200,"type":"textarea"},{"group":"内容节奏","key":"rhythm_increase","label":"内容节奏：增加","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":210,"type":"textarea"},{"group":"内容节奏","key":"rhythm_stable","label":"内容节奏：稳定","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":220,"type":"textarea"},{"key":"recommendation_reason","label":"推荐理由","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":230,"type":"textarea"},{"key":"build_notify","label":"搭建注意","required":true,"searchable":true,"section":"DIRECTOR_ANALYSIS","sort":240,"type":"textarea"},{"allowUnlimited":true,"dictType":"zsjos_persona_type","key":"adapted_persona_types","label":"适配账号类型","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":250,"type":"dict-multi"},{"allowUnlimited":true,"dictType":"zsjos_material_profession","key":"adapted_business_positions","label":"适配业务定位","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":260,"type":"dict-multi"},{"key":"ip_account_advice","label":"IP/账号适配建议","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":270,"type":"textarea"},{"key":"homepage_build_advice","label":"主页搭建建议","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":280,"type":"textarea"},{"key":"lead_conversion_path","label":"客资转化路径","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":290,"type":"textarea"},{"key":"s1_stage_plan","label":"S1定位期","placeholder":"作品数条数、内容方向、参考验收标准","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":300,"type":"textarea"},{"key":"s2_stage_plan","label":"S2冷启动期","placeholder":"作品数条数、内容方向、参考验收标准","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":310,"type":"textarea"},{"key":"s3_stage_plan","label":"S3内容验证期","placeholder":"矩阵内容节奏、作品数条数、内容方向、参考验收标准","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":320,"type":"textarea"},{"key":"s4_stage_plan","label":"S4咨询验证期","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":330,"type":"textarea"},{"key":"s5_stage_plan","label":"S5客资验证期","placeholder":"客资提取方式、客资","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":340,"type":"textarea"},{"children":[{"key":"matrix_content","label":"矩阵内容","required":true,"searchable":true,"sort":10,"type":"textarea"},{"key":"proportion","label":"占比","required":true,"searchable":true,"sort":20,"type":"text"},{"key":"purpose","label":"目的","required":true,"searchable":true,"sort":30,"type":"text"},{"key":"acceptance_standard","label":"参考验收标准","required":true,"searchable":true,"sort":40,"type":"textarea"}],"key":"s6_stage_plan","label":"S6稳定增长期","minCount":1,"required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":350,"type":"repeat-group"},{"key":"overall_lead_extraction_method","label":"客资提取方式","required":true,"searchable":true,"section":"BUILD_SUGGESTION","sort":360,"type":"textarea"}]';
  SET v_canonical_hash=SHA2(v_fields,256);

  START TRANSACTION;
  OPEN viral_types;
  read_loop: LOOP
    FETCH viral_types INTO v_type_id,v_tenant_id,v_current_id;
    IF done=1 THEN
      LEAVE read_loop;
    END IF;

    SET v_source_id=(
      SELECT MAX(`id`) FROM `zsjos_material_schema_version`
      WHERE `id`=v_current_id AND `material_type_id`=v_type_id
        AND `tenant_id`=v_tenant_id AND `deleted`=b'0'
    );
    IF v_source_id IS NULL THEN
      SET v_source_id=(
        SELECT MAX(`id`) FROM `zsjos_material_schema_version`
        WHERE `material_type_id`=v_type_id AND `tenant_id`=v_tenant_id AND `deleted`=b'0'
      );
    END IF;
    SET v_source_hash=(
      SELECT MAX(`schema_hash`) FROM `zsjos_material_schema_version`
      WHERE `id`=v_source_id AND `material_type_id`=v_type_id
        AND `tenant_id`=v_tenant_id AND `deleted`=b'0'
    );

    IF v_source_id IS NULL
       OR BINARY v_source_hash IN (
         '25df599cf13a2f333b0faa545de2d5e99d4a0ecf789cbfa2a8cf45e60217e31e',
         'b1369fe4d75afb37b65e1cd8837505b14c0feedc9ffe6df4c0f46fd6d1044e59'
       ) THEN
      SET v_target_id=(
        SELECT MAX(`id`) FROM `zsjos_material_schema_version`
        WHERE `material_type_id`=v_type_id AND `tenant_id`=v_tenant_id
          AND BINARY `schema_hash`=BINARY v_canonical_hash AND `deleted`=b'0'
      );
      IF v_target_id IS NULL THEN
        SET v_next_version=(
          SELECT COALESCE(MAX(`version_no`),0)+1 FROM `zsjos_material_schema_version`
          WHERE `material_type_id`=v_type_id AND `tenant_id`=v_tenant_id AND `deleted`=b'0'
        );
        INSERT INTO `zsjos_material_schema_version`
          (`material_type_id`,`version_no`,`status`,`fields_json`,`schema_hash`,
           `published_by_user_id`,`published_at`,`version`,`creator`,`create_time`,
           `updater`,`update_time`,`deleted`,`tenant_id`)
        VALUES
          (v_type_id,v_next_version,'PUBLISHED',CAST(v_fields AS JSON),v_canonical_hash,
           NULL,NOW(),0,'V208',NOW(),'V208',NOW(),b'0',v_tenant_id);
        SET v_target_id=LAST_INSERT_ID();
      END IF;
      IF v_source_id IS NOT NULL AND v_source_id<>v_target_id THEN
        UPDATE `zsjos_material_schema_version`
        SET `status`='ARCHIVED',`version`=`version`+1,`updater`='V208',`update_time`=NOW()
        WHERE `id`=v_source_id AND `material_type_id`=v_type_id AND `tenant_id`=v_tenant_id
          AND `status`='PUBLISHED' AND `deleted`=b'0';
      END IF;
      UPDATE `zsjos_material_schema_version`
      SET `version`=`version`+IF(`status`='PUBLISHED',0,1),
          `status`='PUBLISHED',`published_at`=COALESCE(`published_at`,NOW()),
          `updater`='V208',`update_time`=NOW()
      WHERE `id`=v_target_id AND `material_type_id`=v_type_id
        AND `tenant_id`=v_tenant_id AND `deleted`=b'0';
      UPDATE `zsjos_material_type`
      SET `current_schema_version_id`=v_target_id,`version`=`version`+1,
          `updater`='V208',`update_time`=NOW()
      WHERE `id`=v_type_id AND `tenant_id`=v_tenant_id
        AND (`current_schema_version_id` IS NULL OR `current_schema_version_id`<>v_target_id)
        AND `deleted`=b'0';
    ELSEIF BINARY v_source_hash=BINARY v_canonical_hash AND v_current_id IS NULL THEN
      UPDATE `zsjos_material_schema_version`
      SET `version`=`version`+IF(`status`='PUBLISHED',0,1),
          `status`='PUBLISHED',`published_at`=COALESCE(`published_at`,NOW()),
          `updater`='V208',`update_time`=NOW()
      WHERE `id`=v_source_id AND `material_type_id`=v_type_id
        AND `tenant_id`=v_tenant_id AND `deleted`=b'0';
      UPDATE `zsjos_material_type`
      SET `current_schema_version_id`=v_source_id,`version`=`version`+1,
          `updater`='V208',`update_time`=NOW()
      WHERE `id`=v_type_id AND `tenant_id`=v_tenant_id
        AND `current_schema_version_id` IS NULL AND `deleted`=b'0';
    END IF;
  END LOOP;
  CLOSE viral_types;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V208','viral account template V3',SHA2('V208__viral_account_template_v3.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `checksum`=VALUES(`checksum`),`description`=VALUES(`description`);
  INSERT INTO `zsjos_module_schema_version`
    (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V208','viral account template V3',
          SHA2('V208__viral_account_template_v3.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `checksum`=VALUES(`checksum`),`description`=VALUES(`description`);
  COMMIT;
END$$
DELIMITER ;

CALL `zsjos_V208_apply`();
DROP PROCEDURE IF EXISTS `zsjos_V208_apply`;
