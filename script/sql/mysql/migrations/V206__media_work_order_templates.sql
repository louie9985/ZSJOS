-- V206: converge media work-order templates and add filming field-work template.
-- Additive, repeatable, tenant-scoped configuration only; no business rows are deleted.
SET NAMES utf8mb4;
INSERT INTO `zsjos_work_order_scene`
(`tenant_id`,`code`,`name`,`remark`,`assignment_mode`,`fields_json`,`status`,`category_value`,`category_label_snapshot`,`icon`,`sort`,`processor_type`,`allowed_assignment_types_json`,`source_qualification_mode`,`target_qualification_mode`,`rejection_strategy`,`number_prefix`,`number_reset_period`,`number_sequence_width`,`lifecycle_status`,`version`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT t.id, s.code, s.name, s.remark, NULL, s.fields_json, 1, 'media', '媒体工单', s.icon, s.sort, s.processor_type, '["PERSON","DEPARTMENT"]', 'PERMISSION', 'PERMISSION', 'AVAILABLE', s.prefix, 'DAILY', 6, 'PUBLISHED', 0, 'V206', NOW(), 'V206', NOW(), b'0'
FROM system_tenant t
JOIN (
 SELECT 'media_design_edit' code,'剪辑设计工单' name,'剪辑设计工单字段模板' remark,'ep:edit' icon,10 sort,'PRODUCTION_TICKET' processor_type,'CD' prefix,
 '[{"key":"deadline_at","label":"截止时间","type":"datetime","required":true},{"key":"work_name","label":"作品名","type":"text","required":true},{"key":"account_link","label":"账号链接","type":"text"},{"key":"cover_requirement","label":"封面要求","type":"textarea"},{"key":"duration_requirement","label":"时长要求","type":"text"},{"key":"style_requirement","label":"风格要求","type":"textarea"},{"key":"picture_requirement","label":"画面要求","type":"textarea"},{"key":"subtitle_requirement","label":"字幕要求","type":"textarea"},{"key":"hook_requirement","label":"钩子要求","type":"textarea"},{"key":"original_work_link","label":"原稿作品链接","type":"text"},{"key":"reference_work_link","label":"参考作品链接","type":"text"}]' fields_json
 UNION ALL SELECT 'filming_field_work','拍摄外勤工单','拍摄外勤工单字段模板','ep:camera',20,'FILMING_FIELD_WORK','FW',
 '[{"key":"shoot_start_at","label":"拍摄开始时间","type":"datetime","required":true},{"key":"shoot_end_at","label":"拍摄结束时间","type":"datetime","required":true},{"key":"shoot_subject","label":"拍摄主题","type":"text","required":true},{"key":"account_link","label":"账号链接","type":"text"},{"key":"shoot_requirement","label":"拍摄要求","type":"textarea"},{"key":"business_trip","label":"是否出差","type":"dictionary","dictionaryType":"zsjos_yes_no"},{"key":"shoot_location","label":"拍摄地点","type":"text"},{"key":"work_quantity","label":"拍摄作品数量","type":"number"},{"key":"equipment_requirement","label":"设备要求","type":"textarea"},{"key":"reference_work_link","label":"参考作品链接","type":"text"}]' fields_json
) s ON 1=1
WHERE t.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `zsjos_work_order_scene` x WHERE x.tenant_id=t.id AND x.code=s.code AND x.deleted=b'0');
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`) VALUES ('V206','Media work-order template convergence',SHA2('V206__media_work_order_templates.sql',256),NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V206','Media work-order template convergence',SHA2('V206__media_work_order_templates.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_work_order_scene_version`
(`tenant_id`,`scene_id`,`version_no`,`code`,`name`,`remark`,`category_value`,`category_label_snapshot`,`icon`,`sort`,`processor_type`,`allowed_assignment_types_json`,`source_qualification_mode`,`target_qualification_mode`,`rejection_strategy`,`number_prefix`,`number_reset_period`,`number_sequence_width`,`fields_json`,`published_by`,`published_at`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT s.tenant_id,s.id,1,s.code,s.name,s.remark,'media','媒体工单',s.icon,s.sort,s.processor_type,s.allowed_assignment_types_json,'PERMISSION','PERMISSION','AVAILABLE',s.number_prefix,s.number_reset_period,s.number_sequence_width,s.fields_json,1,NOW(),'V206',NOW(),'V206',NOW(),b'0'
FROM `zsjos_work_order_scene` s
WHERE s.code IN ('media_design_edit','filming_field_work') AND s.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM `zsjos_work_order_scene_version` v WHERE v.tenant_id=s.tenant_id AND v.scene_id=s.id AND v.version_no=1 AND v.deleted=b'0');
UPDATE `zsjos_work_order_scene` s JOIN `zsjos_work_order_scene_version` v ON v.tenant_id=s.tenant_id AND v.scene_id=s.id AND v.version_no=1 AND v.deleted=b'0'
SET s.status=1,s.lifecycle_status='PUBLISHED',s.published_version_id=v.id,s.published_version_no=1,s.updater='V206',s.update_time=NOW()
WHERE s.code IN ('media_design_edit','filming_field_work') AND s.deleted=b'0';
