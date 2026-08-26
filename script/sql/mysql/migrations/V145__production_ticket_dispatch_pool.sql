-- V145: account-scoped production-ticket dispatch, rejection and public claim pool.
-- Dependencies/order: apply after V144; requires user relations, positioning submissions and V102 notifications.
-- Data scope: additive columns/command ledger/permissions/scenes; only unassigned pending_accept tickets become public_pool.
-- Repeatability: guarded DDL and index repair, collision-safe permission identities and version upserts.
-- Recovery: forward-only; retained snapshots/events must not be discarded. No historical assigned ticket is rewritten.

DROP PROCEDURE IF EXISTS `zsjos_v145_schema`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v145_schema`()
BEGIN
  CREATE TABLE IF NOT EXISTS `zsjos_production_ticket_command` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `operator_user_id` bigint NOT NULL,
    `idempotency_key` varchar(64) NOT NULL,
    `action_type` varchar(32) NOT NULL,
    `account_id` bigint DEFAULT NULL,
    `ticket_id` bigint DEFAULT NULL,
    `expected_version` int DEFAULT NULL,
    `request_fingerprint` char(64) NOT NULL,
    `result_json` longtext DEFAULT NULL,
    `completed` bit(1) NOT NULL DEFAULT b'0',
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_operator_idempotency` (`tenant_id`,`operator_user_id`,`idempotency_key`),
    KEY `idx_tenant_ticket_action` (`tenant_id`,`ticket_id`,`action_type`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拍剪工单命令幂等记录';
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_user_relation_scene' AND column_name='target_eligibility_type') THEN
    ALTER TABLE `zsjos_user_relation_scene` ADD COLUMN `target_eligibility_type` varchar(16) NOT NULL DEFAULT 'post' COMMENT '目标资格：post/permission' AFTER `target_post_code`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_user_relation_scene' AND column_name='target_permission_code') THEN
    ALTER TABLE `zsjos_user_relation_scene` ADD COLUMN `target_permission_code` varchar(128) DEFAULT NULL COMMENT '目标功能权限码' AFTER `target_eligibility_type`;
  END IF;
  ALTER TABLE `zsjos_user_relation_scene` MODIFY COLUMN `target_post_code` varchar(64) DEFAULT NULL COMMENT '目标岗位编码';
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_production_ticket' AND column_name='positioning_submission_id') THEN
    ALTER TABLE `zsjos_production_ticket` ADD COLUMN `positioning_submission_id` bigint DEFAULT NULL COMMENT '发起时已确认定位提交ID' AFTER `reviewer_user_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_production_ticket' AND column_name='dispatch_context_snapshot_json') THEN
    ALTER TABLE `zsjos_production_ticket` ADD COLUMN `dispatch_context_snapshot_json` json DEFAULT NULL COMMENT '派单账号与定位上下文快照' AFTER `positioning_submission_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_production_ticket' AND column_name='idempotency_key') THEN
    ALTER TABLE `zsjos_production_ticket` ADD COLUMN `idempotency_key` varchar(64) DEFAULT NULL COMMENT '创建幂等键' AFTER `dispatch_context_snapshot_json`;
  END IF;
  ALTER TABLE `zsjos_production_ticket` MODIFY COLUMN `expected_delivered_at` datetime DEFAULT NULL,
    MODIFY COLUMN `deadline_at` datetime DEFAULT NULL, MODIFY COLUMN `max_revision_count` int DEFAULT NULL;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
             AND table_name='zsjos_production_ticket' AND index_name='uk_tenant_create_idempotency')
     AND (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
          FROM information_schema.statistics WHERE table_schema=DATABASE()
            AND table_name='zsjos_production_ticket' AND index_name='uk_tenant_create_idempotency')
         <> 'tenant_id,idempotency_key' THEN
    IF EXISTS (SELECT 1 FROM `zsjos_production_ticket`
               WHERE `idempotency_key` IS NOT NULL
               GROUP BY `tenant_id`,`idempotency_key` HAVING COUNT(*)>1 LIMIT 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V145 blocked: duplicate production-ticket create idempotency keys';
    END IF;
    ALTER TABLE `zsjos_production_ticket` DROP INDEX `uk_tenant_create_idempotency`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                 AND table_name='zsjos_production_ticket' AND index_name='uk_tenant_create_idempotency') THEN
    IF EXISTS (SELECT 1 FROM `zsjos_production_ticket`
               WHERE `idempotency_key` IS NOT NULL
               GROUP BY `tenant_id`,`idempotency_key` HAVING COUNT(*)>1 LIMIT 1) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V145 blocked: duplicate production-ticket create idempotency keys';
    END IF;
    ALTER TABLE `zsjos_production_ticket`
      ADD UNIQUE KEY `uk_tenant_create_idempotency` (`tenant_id`,`idempotency_key`);
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v145_schema`();
DROP PROCEDURE `zsjos_v145_schema`;

DROP PROCEDURE IF EXISTS `zsjos_v145_assert_menu_contract`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v145_assert_menu_contract`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6977
                 AND `permission`='zsjos:production-ticket:query' AND `type`=2 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V145 blocked: production-ticket page menu 6977 is missing';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
             WHERE (`id`=73520 AND NOT (`permission`<=>'zsjos:production-ticket:reject-assignment'))
                OR (`id`=73521 AND NOT (`permission`<=>'zsjos:production-ticket:pool-query'))
                OR (`id`=73522 AND NOT (`permission`<=>'zsjos:production-ticket:claim'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V145 blocked: production-ticket permission menu ID collision';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
             WHERE (`permission`='zsjos:production-ticket:reject-assignment' AND `id`<>73520)
                OR (`permission`='zsjos:production-ticket:pool-query' AND `id`<>73521)
                OR (`permission`='zsjos:production-ticket:claim' AND `id`<>73522)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V145 blocked: production-ticket permission identity collision';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v145_assert_menu_contract`();
DROP PROCEDURE `zsjos_v145_assert_menu_contract`;

START TRANSACTION;

UPDATE `zsjos_production_ticket` SET `status`='public_pool',`updater`='migration-V145',`update_time`=NOW(),`version`=`version`+1
WHERE `status`='pending_accept' AND `assignee_filming_editor_user_id` IS NULL AND `deleted`=b'0';

INSERT INTO `zsjos_user_relation_scene`
(`name`,`code`,`source_label`,`target_label`,`source_post_code`,`target_post_code`,`target_eligibility_type`,`target_permission_code`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT '运营指定剪拍专员','new_media_operator_filming_editor','新媒体运营','剪拍专员','new_media_operator',NULL,'permission','zsjos:production-ticket:accept',0,'人员关系由管理员维护；目标用户还须实时拥有接单权限','migration-V145',NOW(),'migration-V145',NOW(),b'0',tenant.id
FROM `system_tenant` tenant WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `zsjos_user_relation_scene` scene WHERE scene.tenant_id=tenant.id AND scene.code='new_media_operator_filming_editor' AND scene.deleted=b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(73520,'拒绝指定拍剪单','zsjos:production-ticket:reject-assignment',3,11,6977,'','','',NULL,0,b'1',b'1',b'0','migration-V145',NOW(),'migration-V145',NOW(),b'0'),
(73521,'查看拍剪公共池','zsjos:production-ticket:pool-query',3,12,6977,'','','',NULL,0,b'1',b'1',b'0','migration-V145',NOW(),'migration-V145',NOW(),b'0'),
(73522,'抢拍剪公共池工单','zsjos:production-ticket:claim',3,13,6977,'','','',NULL,0,b'1',b'1',b'0','migration-V145',NOW(),'migration-V145',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`parent_id`=VALUES(`parent_id`),`updater`='migration-V145',`update_time`=NOW(),`deleted`=b'0';

UPDATE `system_role_menu` rm JOIN `system_role` role ON role.id=rm.role_id JOIN `system_menu` menu ON menu.id=rm.menu_id
SET rm.deleted=b'1',rm.updater='migration-V145',rm.update_time=NOW()
WHERE role.code='new_media_operator' AND role.deleted=b'0' AND rm.deleted=b'0' AND menu.permission IN ('zsjos:production-ticket:accept','zsjos:production-ticket:reject-assignment','zsjos:production-ticket:produce','zsjos:production-ticket:submit','zsjos:production-ticket:edit','zsjos:production-ticket:over-entitlement','zsjos:production-ticket:pool-query','zsjos:production-ticket:claim');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role.id,menu.id,'migration-V145',NOW(),'migration-V145',NOW(),b'0',role.tenant_id FROM `system_role` role JOIN `system_menu` menu ON menu.deleted=b'0'
WHERE role.deleted=b'0' AND role.status=0 AND ((role.code='new_media_operator' AND menu.permission IN ('zsjos:production-ticket:query','zsjos:production-ticket:create','zsjos:production-ticket:check')) OR (role.code='filming_editor' AND menu.permission IN ('zsjos:production-ticket:query','zsjos:production-ticket:accept','zsjos:production-ticket:reject-assignment','zsjos:production-ticket:produce','zsjos:production-ticket:submit','zsjos:production-ticket:pool-query','zsjos:production-ticket:claim')))
AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing WHERE existing.role_id=role.id AND existing.menu_id=menu.id AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

INSERT INTO `system_notify_template` (`name`,`code`,`nickname`,`scene_code`,`channel_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.name,seed.code,'中世健消息中心',seed.scene,'in_app',seed.title,seed.summary,seed.content,2,'["bizNo"]',0,'V145 拍剪派单结果通知','migration-V145',NOW(),'migration-V145',NOW(),b'0' FROM (
 SELECT '拍剪指定单被拒接' name,'ZSJOS_MEDIA_TICKET_ASSIGNMENT_REJECTED' code,'media.ticket.assignment_rejected' scene,'拍剪指定单被拒接' title,'指定拍剪工单已进入公共池' summary,'拍剪工单 {{bizNo}} 被拒接，现已进入公共池。' content
 UNION ALL SELECT '拍剪公共池已被抢单','ZSJOS_MEDIA_TICKET_CLAIMED','media.ticket.claimed','拍剪工单已被抢单','公共池工单已有剪拍专员接单','拍剪工单 {{bizNo}} 已从公共池被接单。'
) seed WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing WHERE existing.code=seed.code AND existing.deleted=b'0');

INSERT INTO `system_notify_rule` (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.name,seed.scene,'in_app',template.id,'["assignee"]','[]','business_detail',0,'migration-V145',NOW(),'migration-V145',NOW(),b'0',tenant.id FROM `system_tenant` tenant JOIN (
 SELECT '拍剪指定单拒接通知' name,'media.ticket.assignment_rejected' scene,'ZSJOS_MEDIA_TICKET_ASSIGNMENT_REJECTED' code
 UNION ALL SELECT '拍剪公共池抢单通知','media.ticket.claimed','ZSJOS_MEDIA_TICKET_CLAIMED'
) seed JOIN `system_notify_template` template ON template.code=seed.code AND template.deleted=b'0' WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing WHERE existing.tenant_id=tenant.id AND existing.scene_code=seed.scene AND existing.deleted=b'0');

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`) VALUES ('V145','Production-ticket account dispatch and public pool','V145__production_ticket_dispatch_pool.sql',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version` (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`) VALUES ('core','V145','Production-ticket account dispatch and public pool',SHA2('V145__production_ticket_dispatch_pool.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
