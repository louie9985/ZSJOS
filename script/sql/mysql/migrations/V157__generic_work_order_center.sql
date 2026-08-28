-- V157: additive generic work-order center template/version and snapshot fields.
-- Depends on V156. No business rows are deleted or rewritten.
DROP PROCEDURE IF EXISTS `zsjos_v157_add_column`;
DROP PROCEDURE IF EXISTS `zsjos_v157_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v157_add_column`(IN p_table varchar(64), IN p_column varchar(64), IN p_definition text)
BEGIN
  DECLARE CONTINUE HANDLER FOR SQLSTATE '42S21' BEGIN END;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_column) THEN
    /* Derive the column name from the parameter so batch clients cannot turn the
       quoted identifier embedded in p_definition into an empty identifier. */
    SET @zsjos_v157_definition=TRIM(p_definition);
    IF LEFT(@zsjos_v157_definition,1)='`' THEN
      SET @zsjos_v157_definition=TRIM(SUBSTRING(@zsjos_v157_definition,LOCATE('`',@zsjos_v157_definition,2)+1));
    END IF;
    SET @zsjos_v157_sql=CONCAT('ALTER TABLE `',p_table,'` ADD COLUMN `',p_column,'` ',@zsjos_v157_definition);
    PREPARE zsjos_v157_stmt FROM @zsjos_v157_sql; EXECUTE zsjos_v157_stmt; DEALLOCATE PREPARE zsjos_v157_stmt;
  END IF;
END$$
CREATE PROCEDURE `zsjos_v157_apply`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V156') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V157 requires V156';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_scene_version') THEN
    CREATE TABLE `zsjos_work_order_scene_version` (
      `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 0, `scene_id` bigint NOT NULL,
      `version_no` int NOT NULL, `code` varchar(64) NOT NULL, `name` varchar(128) NOT NULL, `remark` varchar(500) DEFAULT NULL,
      `category_value` varchar(100) NOT NULL, `category_label_snapshot` varchar(128) DEFAULT NULL, `icon` varchar(64) DEFAULT NULL,
      `sort` int NOT NULL DEFAULT 0, `processor_type` varchar(32) NOT NULL, `allowed_assignment_types_json` json NOT NULL,
      `source_qualification_mode` varchar(32) NOT NULL, `source_role_scopes_json` json DEFAULT NULL, `source_dept_scopes_json` json DEFAULT NULL,
      `target_qualification_mode` varchar(32) NOT NULL, `target_role_scopes_json` json DEFAULT NULL, `target_dept_scopes_json` json DEFAULT NULL,
      `rejection_strategy` varchar(32) NOT NULL, `number_prefix` varchar(12) NOT NULL, `number_reset_period` varchar(16) NOT NULL,
      `number_sequence_width` int NOT NULL, `fields_json` json NOT NULL, `published_by` bigint NOT NULL, `published_at` datetime NOT NULL,
      `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0', `deleted_time` datetime DEFAULT NULL,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_scene_version` (`tenant_id`,`scene_id`,`version_no`), KEY `idx_scene_code` (`tenant_id`,`code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工单模板发布版本';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_number_counter') THEN
    CREATE TABLE `zsjos_work_order_number_counter` (
      `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 0, `number_prefix` varchar(12) NOT NULL,
      `reset_key` varchar(16) NOT NULL, `current_value` bigint NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
      `deleted_time` datetime DEFAULT NULL, PRIMARY KEY (`id`), UNIQUE KEY `uk_tenant_prefix_period` (`tenant_id`,`number_prefix`,`reset_key`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工单编号计数器';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_work_order_attachment') THEN
    CREATE TABLE `zsjos_work_order_attachment` (
      `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT 0, `work_order_id` bigint NOT NULL,
      `round_no` int NOT NULL, `phase` varchar(16) NOT NULL, `file_id` bigint NOT NULL,
      `file_name_snapshot` varchar(255) NOT NULL, `mime_type_snapshot` varchar(128) DEFAULT NULL,
      `file_size_snapshot` bigint DEFAULT NULL, `sort` int NOT NULL DEFAULT 0, `creator` varchar(64) DEFAULT '',
      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      `deleted` bit(1) NOT NULL DEFAULT b'0', `deleted_time` datetime DEFAULT NULL,
      PRIMARY KEY (`id`), UNIQUE KEY `uk_order_phase_round_file` (`tenant_id`,`work_order_id`,`phase`,`round_no`,`file_id`),
      KEY `idx_order_attachment` (`tenant_id`,`work_order_id`,`round_no`,`phase`,`sort`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 工单附件快照';
  END IF;
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','category_value','`category_value` varchar(100) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','category_label_snapshot','`category_label_snapshot` varchar(128) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','icon','`icon` varchar(64) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','sort','`sort` int NOT NULL DEFAULT 0');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','processor_type','`processor_type` varchar(32) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','allowed_assignment_types_json','`allowed_assignment_types_json` json DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','source_qualification_mode','`source_qualification_mode` varchar(32) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','source_role_scopes_json','`source_role_scopes_json` json DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','source_dept_scopes_json','`source_dept_scopes_json` json DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','target_qualification_mode','`target_qualification_mode` varchar(32) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','target_role_scopes_json','`target_role_scopes_json` json DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','target_dept_scopes_json','`target_dept_scopes_json` json DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','rejection_strategy','`rejection_strategy` varchar(32) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','number_prefix','`number_prefix` varchar(12) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','number_reset_period','`number_reset_period` varchar(16) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','number_sequence_width','`number_sequence_width` int DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','lifecycle_status','`lifecycle_status` varchar(16) NOT NULL DEFAULT ''DRAFT''');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','published_version_id','`published_version_id` bigint DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_scene','published_version_no','`published_version_no` int DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','scene_version_id','`scene_version_id` bigint DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','business_id','`business_id` bigint DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','processor_type','`processor_type` varchar(32) NOT NULL DEFAULT ''GENERIC''');
  CALL `zsjos_v157_add_column`('zsjos_work_order','target_dept_id','`target_dept_id` bigint DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','remark','`remark` varchar(2000) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','current_round','`current_round` int NOT NULL DEFAULT 1');
  CALL `zsjos_v157_add_column`('zsjos_work_order','completion_remark','`completion_remark` varchar(4000) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','completion_attachment_ids_json','`completion_attachment_ids_json` json DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','rejection_strategy_snapshot','`rejection_strategy_snapshot` varchar(32) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','candidate_qualification_mode','`candidate_qualification_mode` varchar(32) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','candidate_role_scopes_json','`candidate_role_scopes_json` json DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order','candidate_dept_scopes_json','`candidate_dept_scopes_json` json DEFAULT NULL');
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_work_order'
             AND column_name='business_type' AND character_maximum_length < 32) THEN
    ALTER TABLE `zsjos_work_order` MODIFY COLUMN `business_type` varchar(32) NOT NULL DEFAULT 'GENERIC';
  END IF;
  CALL `zsjos_v157_add_column`('zsjos_work_order_history','round_no','`round_no` int DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_history','result_remark','`result_remark` varchar(4000) DEFAULT NULL');
  CALL `zsjos_v157_add_column`('zsjos_work_order_history','attachment_ids_json','`attachment_ids_json` json DEFAULT NULL');
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_work_order' AND index_name='uk_tenant_business') THEN
    ALTER TABLE `zsjos_work_order` ADD UNIQUE KEY `uk_tenant_business` (`tenant_id`,`business_type`,`business_id`);
  END IF;

  INSERT INTO `system_dict_type` (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT '工单分类','zsjos_work_order_category',0,'管理员维护；初始化不预置业务分类','migration-V157',NOW(),'migration-V157',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type`='zsjos_work_order_category' AND `deleted`=b'0');

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT seed.id,seed.name,seed.permission,seed.type,seed.sort,seed.parent_id,seed.path,seed.icon,seed.component,seed.component_name,seed.render_mode,0,b'1',b'1',b'1','migration-V157',NOW(),'migration-V157',NOW(),b'0'
  FROM (
    SELECT 79972 AS id,'工单中心' AS name,'' AS permission,1 AS type,45 AS sort,0 AS parent_id,
           '/zsjos/work-orders' AS path,'ep:tickets' AS icon,'' AS component,NULL AS component_name,'admin_embed' AS render_mode
    UNION ALL SELECT 79961,'发起工单','zsjos:work-order:create',2,3,79972,'create','ep:edit','zsjos-workbench',NULL,'native'
    UNION ALL SELECT 79962,'可接工单','zsjos:work-order:query',2,4,79972,'available','ep:takeaway-box','zsjos-workbench',NULL,'native'
    UNION ALL SELECT 79963,'我的工单','zsjos:work-order:query',2,5,79972,'mine','ep:list','zsjos-workbench',NULL,'native'
    UNION ALL SELECT 79964,'接单','zsjos:work-order:take',3,1,79963,'','','',NULL,'native'
    UNION ALL SELECT 79965,'认领','zsjos:work-order:claim',3,2,79962,'','','',NULL,'native'
    UNION ALL SELECT 79966,'拒单','zsjos:work-order:reject',3,3,79963,'','','',NULL,'native'
    UNION ALL SELECT 79967,'撤回','zsjos:work-order:withdraw',3,4,79963,'','','',NULL,'native'
    UNION ALL SELECT 79968,'提交完成','zsjos:work-order:complete',3,5,79963,'','','',NULL,'native'
    UNION ALL SELECT 79969,'验收通过','zsjos:work-order:accept',3,6,79963,'','','',NULL,'native'
    UNION ALL SELECT 79970,'打回重做','zsjos:work-order:return',3,7,79963,'','','',NULL,'native'
    UNION ALL SELECT 79971,'不合格终止','zsjos:work-order:terminate',3,8,79963,'','','',NULL,'native'
    UNION ALL SELECT 79973,'工单模板','zsjos:work-order-scene:query',2,1,79972,'templates','ep:setting','zsjos/workOrderTemplate/index','ZsjosWorkOrderTemplate','admin_only'
    UNION ALL SELECT 79974,'创建模板','zsjos:work-order-scene:create',3,1,79973,'','','',NULL,'admin_only'
    UNION ALL SELECT 79975,'编辑草稿','zsjos:work-order-scene:update',3,2,79973,'','','',NULL,'admin_only'
    UNION ALL SELECT 79976,'发布模板','zsjos:work-order-scene:publish',3,3,79973,'','','',NULL,'admin_only'
    UNION ALL SELECT 79977,'运行审计','zsjos:work-order:audit',2,2,79972,'audit','ep:view','zsjos/workOrderAudit/index','ZsjosWorkOrderAudit','admin_only'
    UNION ALL SELECT 79978,'停用模板','zsjos:work-order-scene:disable',3,4,79973,'','','',NULL,'admin_only'
  ) seed WHERE NOT EXISTS (SELECT 1 FROM `system_menu` m WHERE m.id=seed.id);
  UPDATE `system_menu` SET `name`='工单中心',`path`='/zsjos/work-orders',`workbench_render_mode`='admin_embed',
      `updater`='migration-V157',`update_time`=NOW()
    WHERE `id`=79972 AND `deleted`=b'0';
  UPDATE `system_menu` SET `parent_id`=79972,`sort`=CASE `id` WHEN 79961 THEN 3 WHEN 79962 THEN 4 WHEN 79963 THEN 5 END,
      `path`=CASE `id` WHEN 79961 THEN 'create' WHEN 79962 THEN 'available' WHEN 79963 THEN 'mine' END,
      `workbench_render_mode`='native',
      `updater`='migration-V157',`update_time`=NOW()
    WHERE `id` IN (79961,79962,79963) AND `deleted`=b'0';
  UPDATE `system_menu` SET `workbench_render_mode`='admin_only',`updater`='migration-V157',`update_time`=NOW()
    WHERE `id` IN (79973,79977) AND `deleted`=b'0';
  UPDATE `system_menu` SET `deleted`=b'1',`updater`='migration-V157',`update_time`=NOW()
    WHERE `id`=79960 AND `deleted`=b'0';
  UPDATE `system_tenant_package`
    SET `menu_ids`=JSON_REMOVE(`menu_ids`,JSON_UNQUOTE(JSON_SEARCH(`menu_ids`,'one','79960'))),
        `updater`='migration-V157',`update_time`=NOW()
    WHERE `deleted`=b'0' AND JSON_SEARCH(`menu_ids`,'one','79960') IS NOT NULL;
  SET @zsjos_v157_menu_id=79961;
  WHILE @zsjos_v157_menu_id <= 79978 DO
    UPDATE `system_tenant_package`
    SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',@zsjos_v157_menu_id),`updater`='migration-V157',`update_time`=NOW()
    WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6735','$')
      AND NOT JSON_CONTAINS(`menu_ids`,CAST(@zsjos_v157_menu_id AS CHAR),'$');
    SET @zsjos_v157_menu_id=@zsjos_v157_menu_id+1;
  END WHILE;
  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`) VALUES ('V157','Generic work-order center',SHA2('V157__generic_work_order_center.sql',256),NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V157','Generic work-order center',SHA2('V157__generic_work_order_center.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v157_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v157_apply`;
DROP PROCEDURE IF EXISTS `zsjos_v157_add_column`;
