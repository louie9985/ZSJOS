-- EAM V012: add the dictionary-backed custom-field columns declared in schema/eam.sql.
-- Prerequisite: EAM V001 (eam_category_field, eam_asset). Repeatable: every column is guarded.
-- Data scope: schema only; existing category fields and asset rows are not rewritten.
-- Rollback: retain the columns once used; drop them only after the Java VOs stop mapping them.
DROP PROCEDURE IF EXISTS `eam_v012_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v012_apply`()
BEGIN
  DECLARE column_count INT DEFAULT 0;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category_field' AND column_name='option_source';
  IF column_count=0 THEN ALTER TABLE `eam_category_field` ADD COLUMN `option_source` varchar(30) DEFAULT NULL COMMENT '下拉选项来源：STATIC/SYSTEM_DICT' AFTER `options`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_category_field' AND column_name='dict_type';
  IF column_count=0 THEN ALTER TABLE `eam_category_field` ADD COLUMN `dict_type` varchar(100) DEFAULT NULL COMMENT 'System 字典类型编码' AFTER `option_source`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='source_label_snapshot';
  IF column_count=0 THEN ALTER TABLE `eam_asset` ADD COLUMN `source_label_snapshot` varchar(100) DEFAULT NULL COMMENT '来源标签快照' AFTER `source`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='ext_field_labels';
  IF column_count=0 THEN ALTER TABLE `eam_asset` ADD COLUMN `ext_field_labels` json DEFAULT NULL COMMENT '自定义下拉字段标签快照' AFTER `ext_fields`; END IF;
  SELECT COUNT(*) INTO column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='ext_field_dict_types';
  IF column_count=0 THEN ALTER TABLE `eam_asset` ADD COLUMN `ext_field_dict_types` json DEFAULT NULL COMMENT '自定义下拉字段字典类型快照' AFTER `ext_field_labels`; END IF;
END$$
DELIMITER ;
CALL `eam_v012_apply`();
DROP PROCEDURE IF EXISTS `eam_v012_apply`;
