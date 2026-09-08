-- V186 拆分账号维护排期与个人手工日程。
-- Dependencies: V161 media calendar view and V185 partner permission split.
-- Scope: creates one empty tenant business table; updates calendar menu metadata, packages and role-menu grants.
-- Repeatability: table/menu writes are guarded; role revocation is limited to retired menu 73604.
-- Rollback: forward-only; retain personal events, disable the new menu and restore reviewed grants in a later migration.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v186_preflight`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v186_preflight`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V185')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V185') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V186 requires V185 in both schema-version registries';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73600 AND `type`=1
                 AND `path`='/calendar' AND `deleted`=b'0')
     OR NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73601 AND `type`=2
                    AND `parent_id`=73600 AND `path`='overview'
                    AND `permission`='zsjos:media-calendar:query' AND `deleted`=b'0')
     OR NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73602 AND `type`=3
                    AND `parent_id`=73601
                    AND `permission`='zsjos:media-calendar:query-all' AND `deleted`=b'0')
     OR NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73604 AND `type`=2
                    AND `parent_id`=73600
                    AND `permission`='zsjos:media-calendar:all-query' AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V186 requires the V146/V161 calendar menu contract';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73605
             AND (`type`<>2 OR `parent_id`<>73600 OR `permission`<>'zsjos:personal-calendar:query'))
     OR EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73606
             AND (`type`<>3 OR `parent_id`<>73605 OR `permission`<>'zsjos:personal-calendar:create'))
     OR EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73607
             AND (`type`<>3 OR `parent_id`<>73605 OR `permission`<>'zsjos:personal-calendar:update'))
     OR EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73608
             AND (`type`<>3 OR `parent_id`<>73605 OR `permission`<>'zsjos:personal-calendar:delete'))
     OR EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=73609
             AND (`type`<>3 OR `parent_id`<>73601 OR `permission`<>'zsjos:media-calendar:query-managed')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V186 calendar menu ID is already owned by another contract';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v186_preflight`();
DROP PROCEDURE IF EXISTS `zsjos_v186_preflight`;

-- Pre-migration audit: roles inherited by V161, explicit all-account viewers and their affected users/tenants.
SELECT r.tenant_id, r.id AS role_id, r.name AS role_name, r.data_scope,
       MAX(rm.menu_id = 73604 AND rm.deleted = b'0') AS had_shared_calendar,
       MAX(rm.menu_id = 73604 AND rm.deleted = b'0' AND rm.creator = 'migration-V161') AS granted_by_v161,
       MAX(rm.menu_id = 73602 AND rm.deleted = b'0') AS has_account_calendar_all,
       COUNT(DISTINCT CASE WHEN rm.menu_id = 73604 AND rm.deleted = b'0' THEN ur.user_id END) AS affected_user_count
FROM system_role r
LEFT JOIN system_role_menu rm ON rm.role_id = r.id AND rm.tenant_id = r.tenant_id
LEFT JOIN system_user_role ur ON ur.role_id = r.id AND ur.tenant_id = r.tenant_id AND ur.deleted = b'0'
WHERE r.deleted = b'0'
GROUP BY r.tenant_id, r.id, r.name, r.data_scope
HAVING had_shared_calendar = 1 OR has_account_calendar_all = 1;

SELECT DISTINCT ur.tenant_id, ur.user_id, ur.role_id, rm.creator
FROM system_user_role ur
JOIN system_role_menu rm ON rm.role_id=ur.role_id AND rm.tenant_id=ur.tenant_id
WHERE ur.deleted=b'0' AND rm.menu_id=73604 AND rm.deleted=b'0'
ORDER BY ur.tenant_id, ur.role_id, ur.user_id;

START TRANSACTION;

CREATE TABLE IF NOT EXISTS `zsjos_personal_calendar_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日程编号',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `owner_user_id` bigint NOT NULL COMMENT '所属 ADMIN 用户编号',
  `title` varchar(100) NOT NULL COMMENT '标题',
  `description` varchar(2000) DEFAULT NULL COMMENT '说明',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `all_day` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否全天',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型',
  `source_id` bigint DEFAULT NULL COMMENT '来源业务编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_personal_calendar_owner_start` (`tenant_id`,`owner_user_id`,`start_time`,`deleted`),
  CONSTRAINT `chk_personal_calendar_time_range` CHECK (`end_time` >= `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人日程';

UPDATE `system_menu`
SET `status`=1,`visible`=b'0',`updater`='migration-V186',`update_time`=NOW()
WHERE `id`=73604 AND `deleted`=b'0';

UPDATE `system_role_menu`
SET `deleted`=b'1',`updater`='migration-V186',`update_time`=NOW()
WHERE `menu_id`=73604 AND `deleted`=b'0' AND `creator`='migration-V161';

INSERT INTO `system_menu`
 (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
  `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
 (73605,'我的日历','zsjos:personal-calendar:query',2,2,73600,'personal','ep:calendar',
  'zsjos/personalCalendar/index','ZsjosPersonalCalendar','native',0,b'1',b'1',b'1','migration-V186',NOW(),'migration-V186',NOW(),b'0'),
 (73606,'新增个人日程','zsjos:personal-calendar:create',3,1,73605,'','','',NULL,'native',0,b'1',b'1',b'0','migration-V186',NOW(),'migration-V186',NOW(),b'0'),
 (73607,'修改个人日程','zsjos:personal-calendar:update',3,2,73605,'','','',NULL,'native',0,b'1',b'1',b'0','migration-V186',NOW(),'migration-V186',NOW(),b'0'),
 (73608,'删除个人日程','zsjos:personal-calendar:delete',3,3,73605,'','','',NULL,'native',0,b'1',b'1',b'0','migration-V186',NOW(),'migration-V186',NOW(),b'0'),
 (73609,'查看管理范围账号日历','zsjos:media-calendar:query-managed',3,2,73601,'','','',NULL,'native',0,b'1',b'1',b'0','migration-V186',NOW(),'migration-V186',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
 `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
 `component`=VALUES(`component`),`component_name`=VALUES(`component_name`),
 `workbench_render_mode`=VALUES(`workbench_render_mode`),`status`=0,`visible`=VALUES(`visible`),
 `updater`='migration-V186',`update_time`=NOW(),`deleted`=b'0';

UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73605),`updater`='migration-V186',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$') AND NOT JSON_CONTAINS(`menu_ids`,'73605','$');
UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73606),`updater`='migration-V186',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$') AND NOT JSON_CONTAINS(`menu_ids`,'73606','$');
UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73607),`updater`='migration-V186',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$') AND NOT JSON_CONTAINS(`menu_ids`,'73607','$');
UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73608),`updater`='migration-V186',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$') AND NOT JSON_CONTAINS(`menu_ids`,'73608','$');
UPDATE `system_tenant_package` SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',73609),`updater`='migration-V186',`update_time`=NOW()
WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'73600','$') AND NOT JSON_CONTAINS(`menu_ids`,'73609','$');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V186','Split media and personal calendars','V186__split_media_and_personal_calendar.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V186','Split media and personal calendars',SHA2('V186__split_media_and_personal_calendar.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;

SELECT COUNT(*) AS remaining_v161_retired_calendar_grants
FROM system_role_menu
WHERE menu_id=73604 AND deleted=b'0' AND creator='migration-V161';

SELECT creator, COUNT(*) AS remaining_retired_calendar_grants
FROM system_role_menu
WHERE menu_id=73604 AND deleted=b'0'
GROUP BY creator
ORDER BY creator;

SELECT r.tenant_id, r.id AS role_id, r.data_scope,
       MAX(rm.menu_id=73602 AND rm.deleted=b'0') AS has_account_calendar_all,
       MAX(rm.menu_id=73604 AND rm.deleted=b'0') AS has_retired_shared_calendar
FROM system_role r
LEFT JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id
WHERE r.deleted=b'0'
GROUP BY r.tenant_id, r.id, r.data_scope
HAVING has_account_calendar_all=1 OR has_retired_shared_calendar=1;
