-- V100: new-media role menu permissions and director-owned student page.
-- Draft only. Do not execute without separate authorization.
SET NAMES utf8mb4;
SET @v100_workbench_menu_id := (SELECT id FROM system_menu WHERE path='/zsjos' AND parent_id=0 AND deleted=b'0' ORDER BY id LIMIT 1);

INSERT IGNORE INTO system_menu
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(7022,'我的学员','zsjos:media-student:query-my',2,47,@v100_workbench_menu_id,'/zsjos/media-students','ep:user','zsjos-workbench','MediaStudentsPage',0,b'1',b'1',b'1','migration-V100',NOW(),'migration-V100',NOW(),b'0');

-- The director role must not receive the planner-owned menu.
DELETE rm FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
WHERE r.code='content_director' AND rm.menu_id=73020 AND rm.deleted=b'0';

-- New-media operator: full new-media workbench.
INSERT IGNORE INTO system_role_menu (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V100',NOW(),'migration-V100',NOW(),b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.id IN (6970,6971,6972,6973,6974,6975,6976,6987,6988,6989,6990,6991,6977,6978,6979,6992,6993,6994,6980,6981,6982,6995,6996,6997,6984,6985,7022)
WHERE r.code='new_media_operator' AND r.status=0 AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

-- Content director: account/content/positioning/review plus director-owned students.
INSERT IGNORE INTO system_role_menu (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V100',NOW(),'migration-V100',NOW(),b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.id IN (6970,6971,6972,6973,6974,6975,6976,6987,6988,6989,6990,6991,6980,6981,6982,6995,6996,6997,6985,7022)
WHERE r.code='content_director' AND r.status=0 AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

-- Filming editor: production ticket and content-item operations only.
INSERT IGNORE INTO system_role_menu (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.id,m.id,'migration-V100',NOW(),'migration-V100',NOW(),b'0',r.tenant_id
FROM system_role r JOIN system_menu m ON m.id IN (6977,6978,6979,6992,6993,6994,7007)
WHERE r.code='filming_editor' AND r.status=0 AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu x WHERE x.role_id=r.id AND x.menu_id=m.id AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V100','New-media role menu permissions and director student page','new-media-role-menu-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V100','New-media role menu permissions and director student page',SHA2('new-media-role-menu-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
