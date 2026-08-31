-- EAM V010: complete asset-transfer BPM snapshots, return inspection and permissions.
-- Prerequisite: EAM V009. Repeatable: every column, menu and dictionary row is guarded.
-- Data scope: schema and configuration only; existing transfer rows are not rewritten.
-- Rollback: disable the new permissions/process for runtime rollback; retain columns once used.
DROP PROCEDURE IF EXISTS `eam_v010_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v010_apply`()
BEGIN
  DECLARE column_count INT DEFAULT 0;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='asset_code_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `asset_code_snapshot` varchar(100) DEFAULT NULL AFTER `asset_id`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='asset_name_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `asset_name_snapshot` varchar(200) DEFAULT NULL AFTER `asset_code_snapshot`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='type_label_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `type_label_snapshot` varchar(50) DEFAULT NULL AFTER `asset_name_snapshot`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='from_employee_name_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `from_employee_name_snapshot` varchar(100) DEFAULT NULL AFTER `from_dept_id`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='from_dept_name_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `from_dept_name_snapshot` varchar(100) DEFAULT NULL AFTER `from_employee_name_snapshot`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='to_employee_name_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `to_employee_name_snapshot` varchar(100) DEFAULT NULL AFTER `to_dept_id`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='to_dept_name_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `to_dept_name_snapshot` varchar(100) DEFAULT NULL AFTER `to_employee_name_snapshot`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='round_no';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `round_no` int NOT NULL DEFAULT 1 AFTER `process_instance_id`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='apply_user_name_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `apply_user_name_snapshot` varchar(100) DEFAULT NULL AFTER `apply_user_id`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='apply_dept_id';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `apply_dept_id` bigint DEFAULT NULL AFTER `apply_user_name_snapshot`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='apply_dept_name_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `apply_dept_name_snapshot` varchar(100) DEFAULT NULL AFTER `apply_dept_id`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='inspection_result';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `inspection_result` tinyint DEFAULT NULL AFTER `apply_time`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='inspection_remark';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `inspection_remark` varchar(500) DEFAULT NULL AFTER `inspection_result`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='inspection_file_urls';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `inspection_file_urls` json DEFAULT NULL AFTER `inspection_remark`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='inspected_by_user_id';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `inspected_by_user_id` bigint DEFAULT NULL AFTER `inspection_file_urls`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='inspected_at';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `inspected_at` datetime DEFAULT NULL AFTER `inspected_by_user_id`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_transfer' AND column_name='version';
  IF column_count=0 THEN ALTER TABLE `eam_transfer` ADD COLUMN `version` int NOT NULL DEFAULT 0 AFTER `inspected_at`; END IF;

  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7206,'取消流转','eam:transfer:cancel',3,4,7103,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V010',NOW(),'migration-eam-V010',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:transfer:cancel' AND deleted=b'0');
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7207,'流转验收','eam:transfer:inspect',3,5,7103,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V010',NOW(),'migration-eam-V010',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:transfer:inspect' AND deleted=b'0');
  INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 7208,'发起资产流转','eam:workbench:asset:transfer',3,4,7200,'','','',NULL,0,b'1',b'1',b'1','migration-eam-V010',NOW(),'migration-eam-V010',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='eam:workbench:asset:transfer' AND deleted=b'0');

  INSERT INTO `system_dict_data` (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT s.sort,s.label,s.value,'eam_transfer_status',0,s.color_type,'','','migration-eam-V010',NOW(),'migration-eam-V010',NOW(),b'0'
  FROM (SELECT 5 sort,'草稿' label,'4' value,'info' color_type UNION ALL SELECT 6,'待验收','5','warning' UNION ALL SELECT 7,'已完成','6','success' UNION ALL SELECT 8,'异常待处理','7','danger') s
  WHERE NOT EXISTS (SELECT 1 FROM system_dict_data d WHERE d.dict_type='eam_transfer_status' AND d.value=s.value AND d.deleted=b'0');
END$$
DELIMITER ;
CALL `eam_v010_apply`();
DROP PROCEDURE IF EXISTS `eam_v010_apply`;
