-- Dependencies/order: V197 and the existing Workbench root menu 6735/material type seed.
-- Scope: one System dictionary type, its eight business options, two Workbench pages,
-- and the existing viral_content display name. No material rows are changed.
-- Repeatability: idempotent by stable dictionary values/menu ids; rollback is forward-only.
SET NAMES utf8mb4;

INSERT INTO system_dict_type (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '爆款作品类型','zsjos_viral_content_type',0,'爆款内容拆解类型；管理员可维护','V198',NOW(),'V198',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_type WHERE `type`='zsjos_viral_content_type' AND deleted=b'0');

INSERT INTO system_dict_data (`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.sort,seed.label,seed.value,'zsjos_viral_content_type',0,'default','V198',NOW(),'V198',NOW(),b'0'
FROM (
  SELECT 10 sort,'流量爆款' label,'traffic' value UNION ALL SELECT 20,'客资爆款','lead'
  UNION ALL SELECT 30,'低粉爆款','low_follower' UNION ALL SELECT 40,'图文爆款','image_text'
  UNION ALL SELECT 50,'热点爆款','hot_topic' UNION ALL SELECT 60,'视频爆款','video'
  UNION ALL SELECT 70,'形式爆款','format' UNION ALL SELECT 80,'内容爆款','content'
) seed
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data d WHERE d.dict_type='zsjos_viral_content_type' AND d.value=seed.value AND d.deleted=b'0');

UPDATE zsjos_material_type SET name='爆款内容', description='爆款内容拆解内容包', updater='V198', update_time=NOW()
WHERE code='viral_content' AND deleted=b'0';

INSERT INTO system_menu (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
 (80041,'爆款账号拆解','zsjos:material:create',2,61,6735,'viral-account-decompose','ep:user','zsjos-workbench','ViralAccountDecomposePage','native',0,b'1',b'1',b'1','V198',NOW(),'V198',NOW(),b'0'),
 (80042,'爆款内容拆解','zsjos:material:create',2,62,6735,'viral-content-decompose','ep:video-camera','zsjos-workbench','ViralContentDecomposePage','native',0,b'1',b'1',b'1','V198',NOW(),'V198',NOW(),b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),sort=VALUES(sort),parent_id=VALUES(parent_id),path=VALUES(path),icon=VALUES(icon),component=VALUES(component),component_name=VALUES(component_name),workbench_render_mode=VALUES(workbench_render_mode),status=0,visible=b'1',updater='V198',update_time=NOW(),deleted=b'0';

UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',80041),updater='V198',update_time=NOW()
WHERE deleted=b'0' AND JSON_CONTAINS(menu_ids,'6735','$') AND NOT JSON_CONTAINS(menu_ids,'80041','$');
UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',80042),updater='V198',update_time=NOW()
WHERE deleted=b'0' AND JSON_CONTAINS(menu_ids,'6735','$') AND NOT JSON_CONTAINS(menu_ids,'80042','$');

INSERT INTO zsjos_schema_version (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V198','Viral content decompose menus and dictionary',SHA2('V198__viral_content_decompose.sql',256),NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V198','Viral content decompose menus and dictionary',SHA2('V198__viral_content_decompose.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
