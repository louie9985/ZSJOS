-- V177: Partner account open-request approval.
-- Depends on V176 core metadata and V170 partner invitation activation.
-- Scope: creates an empty tenant-scoped request table, Workbench menu/button permissions, and default in-app notification rules.
-- It does not seed partner accounts, invitations, users, departments, roles, or external webhook configuration.
-- Repeatable: guarded by table/menu/package/grant checks; records both schema-version registries.
-- Rollback limitation: retain request history and disable menu/notification rules in a later forward migration.

DROP PROCEDURE IF EXISTS `zsjos_v177_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v177_apply`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V176') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V177 requires V176 in schema-version registry';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=6735 AND `deleted`=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V177 requires Workbench ZSJOS root menu 6735';
  END IF;
  IF EXISTS (SELECT 1 FROM `system_menu`
             WHERE `id` IN (80000,80001,80002,80003)
               AND `permission` NOT IN ('zsjos:partner-open-request:query',
                                        'zsjos:partner-open-request:create',
                                        'zsjos:partner-open-request:review',
                                        'zsjos:partner-open-request:cancel')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V177 Partner open-request menu IDs are occupied';
  END IF;

  START TRANSACTION;

  CREATE TABLE IF NOT EXISTS `zsjos_partner_open_request` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '代开通申请编号',
    `request_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请业务编号',
    `partner_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '兼职姓名',
    `partner_mobile` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '兼职手机号',
    `active_mobile_key` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批中手机号唯一键，终态置空',
    `assigned_employee_user_id` bigint NOT NULL COMMENT '归属员工用户编号',
    `assigned_employee_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归属员工姓名快照',
    `assigned_employee_dept_id_snapshot` bigint DEFAULT NULL COMMENT '归属员工部门编号快照',
    `assigned_employee_dept_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '归属员工部门名称快照',
    `applicant_user_id` bigint NOT NULL COMMENT '发起人用户编号',
    `applicant_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发起人姓名快照',
    `applicant_dept_id_snapshot` bigint DEFAULT NULL COMMENT '发起人部门编号快照',
    `applicant_dept_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发起人部门名称快照',
    `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：pending/approved/opened/rejected/cancelled/open_failed',
    `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BPM 流程实例编号',
    `invitation_id` bigint DEFAULT NULL COMMENT '生成的邀请码编号',
    `invite_code_snapshot` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邀请码快照',
    `invite_expires_at` datetime DEFAULT NULL COMMENT '邀请码过期时间',
    `reviewed_by_user_id` bigint DEFAULT NULL COMMENT '审批人用户编号',
    `reviewed_at` datetime DEFAULT NULL COMMENT '审批时间',
    `review_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批意见',
    `opened_at` datetime DEFAULT NULL COMMENT '邀请码生成时间',
    `failure_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邀请码生成失败原因',
    `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发起幂等键',
    `submitted_at` datetime NOT NULL COMMENT '提交时间',
    `cancelled_by_user_id` bigint DEFAULT NULL COMMENT '撤回人用户编号',
    `cancelled_at` datetime DEFAULT NULL COMMENT '撤回时间',
    `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
    `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_request_no` (`tenant_id`,`request_no`),
    UNIQUE KEY `uk_tenant_active_mobile` (`tenant_id`,`active_mobile_key`),
    UNIQUE KEY `uk_tenant_applicant_idempotency` (`tenant_id`,`applicant_user_id`,`idempotency_key`),
    KEY `idx_tenant_applicant_status` (`tenant_id`,`applicant_user_id`,`status`,`submitted_at`),
    KEY `idx_tenant_status_time` (`tenant_id`,`status`,`submitted_at`),
    KEY `idx_tenant_process` (`tenant_id`,`process_instance_id`),
    KEY `idx_tenant_invitation` (`tenant_id`,`invitation_id`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 代开通兼职账号申请';

  INSERT INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
     `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT 80000,'代开通兼职账号','zsjos:partner-open-request:query',2,32,6735,'partner-open-requests','ep:key',
         'zsjos-workbench','PartnerOpenRequestPage','native',0,b'1',b'1',b'1','V177',NOW(),'V177',NOW(),b'0'
  WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id`=80000 AND `deleted`=b'0');

  INSERT IGNORE INTO `system_menu`
    (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
     `workbench_render_mode`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  VALUES
    (80001,'申请代开通兼职账号','zsjos:partner-open-request:create',3,1,80000,'','','',NULL,'native',0,b'1',b'1',b'0','V177',NOW(),'V177',NOW(),b'0'),
    (80002,'审批代开通兼职账号','zsjos:partner-open-request:review',3,2,80000,'','','',NULL,'native',0,b'1',b'1',b'0','V177',NOW(),'V177',NOW(),b'0'),
    (80003,'撤回代开通兼职账号','zsjos:partner-open-request:cancel',3,3,80000,'','','',NULL,'native',0,b'1',b'1',b'0','V177',NOW(),'V177',NOW(),b'0');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',80000),`updater`='V177',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'6735','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'80000','$');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',80001),`updater`='V177',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'80000','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'80001','$');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',80002),`updater`='V177',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'80000','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'80002','$');

  UPDATE `system_tenant_package`
  SET `menu_ids`=JSON_ARRAY_APPEND(`menu_ids`,'$',80003),`updater`='V177',`update_time`=NOW()
  WHERE `deleted`=b'0' AND JSON_CONTAINS(`menu_ids`,'80000','$')
    AND NOT JSON_CONTAINS(`menu_ids`,'80003','$');

  INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT role.id, target.menu_id, 'V177', NOW(), 'V177', NOW(), b'0', role.tenant_id
  FROM `system_role` role
  JOIN (SELECT 80000 AS menu_id UNION ALL SELECT 80001 UNION ALL SELECT 80002 UNION ALL SELECT 80003) target
  WHERE role.code='system_administrator' AND role.status=0 AND role.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `system_role_menu` existing
                    WHERE existing.role_id=role.id AND existing.menu_id=target.menu_id
                      AND existing.tenant_id=role.tenant_id AND existing.deleted=b'0');

  INSERT INTO `system_notify_template`
    (`name`,`code`,`nickname`,`scene_code`,`title`,`summary`,`content`,`type`,`params`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
  SELECT seed.name, seed.code, '中世健员工工作台', seed.scene_code, seed.title, seed.summary, seed.content, 2,
         seed.params, 0, 'V177 代开通兼职账号通知', 'V177', NOW(), 'V177', NOW(), b'0'
  FROM (
    SELECT '代开通兼职账号待审批' AS name,'ZSJOS_PARTNER_OPEN_REQUEST_SUBMITTED' AS code,'zsjos.partner_open_request.submitted' AS scene_code,
           '代开通兼职账号待审批' AS title,'{{partner.name}}的开通申请待审批' AS summary,
           '兼职{{partner.name}}（{{partner.mobile.masked}}）的代开通申请{{request.no}}已提交，请及时审批。' AS content,
           '["partner.name","partner.mobile.masked","request.no"]' AS params
    UNION ALL SELECT '代开通兼职账号已生成邀请码','ZSJOS_PARTNER_OPEN_REQUEST_OPENED','zsjos.partner_open_request.opened',
           '兼职账号邀请码已生成','{{partner.name}}的邀请码为{{invite.code}}',
           '兼职{{partner.name}}（{{partner.mobile.masked}}）的邀请码已生成：{{invite.code}}，过期时间{{invite.expiresAt}}。','["partner.name","partner.mobile.masked","invite.code","invite.expiresAt"]'
    UNION ALL SELECT '代开通兼职账号审批驳回','ZSJOS_PARTNER_OPEN_REQUEST_REJECTED','zsjos.partner_open_request.rejected',
           '代开通兼职账号申请已驳回','{{partner.name}}的开通申请已驳回',
           '兼职{{partner.name}}（{{partner.mobile.masked}}）的代开通申请{{request.no}}已驳回。原因：{{review.reason}}','["partner.name","partner.mobile.masked","request.no","review.reason"]'
    UNION ALL SELECT '代开通兼职账号开通失败','ZSJOS_PARTNER_OPEN_REQUEST_OPEN_FAILED','zsjos.partner_open_request.open_failed',
           '代开通兼职账号开通失败','{{partner.name}}的邀请码生成失败',
           '兼职{{partner.name}}（{{partner.mobile.masked}}）的代开通申请{{request.no}}审批已通过，但邀请码生成失败。原因：{{failure.reason}}','["partner.name","partner.mobile.masked","request.no","failure.reason"]'
  ) seed
  WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` existing
                    WHERE existing.code=seed.code AND existing.deleted=b'0');

  INSERT INTO `system_notify_rule`
    (`name`,`scene_code`,`channel_code`,`template_id`,`recipient_roles`,`specified_user_ids`,`action_type`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
  SELECT seed.name, seed.scene_code, 'in_app', template.id, seed.roles, '[]', 'business_detail', 0,
         'V177', NOW(), 'V177', NOW(), b'0', tenant.id
  FROM `system_tenant` tenant
  JOIN (
    SELECT '代开通兼职账号待审批' AS name,'zsjos.partner_open_request.submitted' AS scene_code,
           'ZSJOS_PARTNER_OPEN_REQUEST_SUBMITTED' AS template_code,'["reviewer"]' AS roles
    UNION ALL SELECT '代开通兼职账号已生成邀请码','zsjos.partner_open_request.opened','ZSJOS_PARTNER_OPEN_REQUEST_OPENED','["applicant"]'
    UNION ALL SELECT '代开通兼职账号审批驳回','zsjos.partner_open_request.rejected','ZSJOS_PARTNER_OPEN_REQUEST_REJECTED','["applicant"]'
    UNION ALL SELECT '代开通兼职账号开通失败','zsjos.partner_open_request.open_failed','ZSJOS_PARTNER_OPEN_REQUEST_OPEN_FAILED','["applicant"]'
  ) seed
  JOIN `system_notify_template` template ON template.code=seed.template_code AND template.deleted=b'0'
  WHERE tenant.deleted=b'0'
    AND NOT EXISTS (SELECT 1 FROM `system_notify_rule` existing
                    WHERE existing.tenant_id=tenant.id AND existing.scene_code=seed.scene_code
                      AND existing.channel_code='in_app' AND existing.deleted=b'0');

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V177','Partner account open-request approval',
          SHA2('V177__partner_open_request.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V177','Partner account open-request approval',
          SHA2('V177__partner_open_request.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  COMMIT;
END$$
DELIMITER ;
CALL `zsjos_v177_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v177_apply`;
