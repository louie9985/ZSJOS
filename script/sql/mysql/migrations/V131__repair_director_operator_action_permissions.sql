-- Repair media-student action grants when the V128 schema exists without its menu relations.
-- Repeatable and non-destructive for business data. It only adds missing action menus/grants and retires
-- confirmed legacy positioning write grants from the new-media operator role. It also restores the
-- administrator-maintained director-to-operator relationship scene without creating user relations.

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.id,seed.name,seed.permission,3,seed.sort,seed.parent_id,'','','','',0,b'1',b'1',b'1','V131',NOW(),'V131',NOW(),b'0'
FROM (
  SELECT 73471 id,'编导资料预审' name,'zsjos:student:director-precheck' permission,71 sort,7022 parent_id
  UNION ALL SELECT 73472,'编导学员采访','zsjos:student:director-interview',72,7022
  UNION ALL SELECT 73473,'编导指派运营','zsjos:student:director-operator-assign',73,7022
  UNION ALL SELECT 73474,'运营确认定位卡','zsjos:positioning-card:operator-confirm',74,7022
  UNION ALL SELECT 73475,'运营退回定位卡','zsjos:positioning-card:operator-reject',75,7022
  UNION ALL SELECT 73476,'查看定位卡','zsjos:positioning-card:query',76,7022
) seed
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` m WHERE m.permission=seed.permission AND m.deleted=b'0')
  AND NOT EXISTS (SELECT 1 FROM `system_menu` m WHERE m.id=seed.id);

UPDATE `system_menu` SET `parent_id`=7022,`updater`='V131',`update_time`=NOW()
WHERE `id` IN (73471,73472,73473,73474,73475,73476) AND `deleted`=b'0' AND `parent_id`<>7022;

UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 73471),`updater`='V131',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`, '7022', '$') AND NOT JSON_CONTAINS(`menu_ids`, '73471', '$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 73472),`updater`='V131',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`, '7022', '$') AND NOT JSON_CONTAINS(`menu_ids`, '73472', '$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 73473),`updater`='V131',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`, '7022', '$') AND NOT JSON_CONTAINS(`menu_ids`, '73473', '$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 73474),`updater`='V131',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`, '7022', '$') AND NOT JSON_CONTAINS(`menu_ids`, '73474', '$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 73475),`updater`='V131',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`, '7022', '$') AND NOT JSON_CONTAINS(`menu_ids`, '73475', '$');
UPDATE `system_tenant_package`
SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`, '$', 73476),`updater`='V131',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`, '7022', '$') AND NOT JSON_CONTAINS(`menu_ids`, '73476', '$');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`tenant_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT r.id,m.id,r.tenant_id,'V131',NOW(),'V131',NOW(),b'0'
FROM `system_role` r JOIN `system_menu` m ON m.permission IN (
  'zsjos:student:director-precheck','zsjos:student:director-interview','zsjos:student:director-operator-assign',
  'zsjos:positioning-card:query','zsjos:positioning-card:create','zsjos:positioning-card:submit-review',
  'zsjos:positioning-card:confirm-trial','zsjos:positioning-card:archive')
WHERE r.code='content_director' AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=m.id
    AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`tenant_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT r.id,m.id,r.tenant_id,'V131',NOW(),'V131',NOW(),b'0'
FROM `system_role` r JOIN `system_menu` m ON m.permission IN (
  'zsjos:positioning-card:query','zsjos:positioning-card:operator-confirm','zsjos:positioning-card:operator-reject')
WHERE r.code='new_media_operator' AND r.deleted=b'0' AND m.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.role_id=r.id AND x.menu_id=m.id
    AND x.tenant_id=r.tenant_id AND x.deleted=b'0');

UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id
JOIN `system_menu` m ON m.id=rm.menu_id
SET rm.deleted=b'1',rm.updater='V131',rm.update_time=NOW()
WHERE r.code='new_media_operator' AND r.deleted=b'0' AND rm.deleted=b'0'
  AND m.permission IN ('zsjos:positioning-card:create','zsjos:positioning-card:edit',
    'zsjos:positioning-card:feasibility-review','zsjos:positioning-card:sign',
    'zsjos:positioning-card:submit-review','zsjos:positioning-card:confirm-trial','zsjos:positioning-card:archive');

INSERT INTO `zsjos_user_relation_scene`
(`name`,`code`,`source_label`,`target_label`,`source_post_code`,`target_post_code`,`status`,`remark`,
 `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '编导分配新媒体运营','content_director_operator','编导','新媒体运营',
       'content_director','new_media_operator',0,'V131 预置场景；人员关系由管理员维护',
       'V131',NOW(),'V131',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
WHERE tenant.deleted=b'0' AND NOT EXISTS (
  SELECT 1 FROM `zsjos_user_relation_scene` scene
  WHERE scene.tenant_id=tenant.id AND scene.code='content_director_operator' AND scene.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V131','repair director/operator actions and relationship scene',
       SHA2('V131__repair_director_operator_action_permissions.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V131');
