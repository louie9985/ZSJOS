-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V248: restore missing menu/button definitions; administrators configure their grants.
-- Apply after V247; repeated menu upserts preserve the existing migration contract.
-- No automatic grant restoration or permission assignment is performed.
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) 重建客资详情 5 个页签权限行 (父节点 6770 客资管理)
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(602300,'查看跟进记录','zsjos:lead-detail:follow-up-read',3,30,6770,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
(602301,'查看申诉记录','zsjos:lead-detail:appeal-read',3,31,6770,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
(602302,'查看投诉记录','zsjos:lead-detail:complaint-read',3,32,6770,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
(602303,'查看订单记录','zsjos:lead-detail:order-read',3,33,6770,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
(602304,'查看流转记录','zsjos:lead-detail:flow-read',3,34,6770,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
(602307,'申请订单退款','zsjos:sales-order:refund-apply',3,42,6810,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
-- 支付退款挂在「支付主体管理」下(财务配置面，admin_embed)
(602308,'退款查询','zsjos:payment-refund:read',3,50,602200,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
(602309,'刷新退款结果','zsjos:payment-refund:refresh',3,51,602200,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
(602310,'财务直接退款','zsjos:payment-refund:direct',3,52,602200,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0'),
-- 学员考期修改挂在「学员管理」下
(602311,'修改学员考期','zsjos:student:exam-date-update',3,50,73020,'','','',NULL,0,b'1',b'1',b'0','V248',NOW(),'V248',NOW(),b'0')
ON DUPLICATE KEY UPDATE
  `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
  `sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`status`=0,`visible`=b'1',
  `updater`='V248',`update_time`=NOW(),`deleted`=b'0';

-- ---------------------------------------------------------------------------
-- 2b) 订单范围权限：zsjos:sales-order:query-own / query-team 由 V025/V136 建在 6813/73510，
--     V195 统一订单管理时将其软删。这两个权限仍在后端被校验(SalesOrderController 的
--     /my-page、/team-page 等)，且 V195 的验证断言要求「不存在这两个权限的菜单行」，
--     因此直接新建菜单行会与既有断言冲突。正确做法是保留软删行不动、不为其建新行，
--     权限的可配置性改由后续独立评审决定(见 README)。
-- ---------------------------------------------------------------------------

-- 版本记录
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V248','补建被覆盖的客资详情与订单退款权限', SHA2('V248__restore_lead_detail_and_order_permissions.sql',256), NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V248','补建被覆盖的客资详情与订单退款权限', SHA2('V248__restore_lead_detail_and_order_permissions.sql',256), '2026.09.15-201500-v248', NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

-- =====================================================================================
-- 只读核对
-- =====================================================================================

-- C1) 5 个客资详情权限现在应各自存在且唯一。预期 5 行, dup=1。
SELECT 'C1-lead-detail' AS check_name, m.permission, COUNT(*) AS rows_found,
       COUNT(DISTINCT m.id) AS distinct_ids
FROM `system_menu` m
WHERE m.`deleted`=b'0' AND m.`permission` LIKE 'zsjos:lead-detail:%'
GROUP BY m.`permission`;

-- C2) 每个角色获得的详情页签权限清单, 供人工核对。
SELECT 'C2-grants' AS check_name, r.code AS role_code,
       GROUP_CONCAT(m.permission ORDER BY m.permission) AS lead_detail_permissions
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`permission` LIKE 'zsjos:lead-detail:%'
GROUP BY r.code ORDER BY r.code;

-- C3) 排除项确认: normal_user 与 teaching_assistant 不得持有任何 lead-detail 权限。预期 0 行。
SELECT 'C3-excluded' AS check_name, r.code, m.permission
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND r.`code` IN ('normal_user','teaching_assistant')
  AND m.`permission` LIKE 'zsjos:lead-detail:%';

-- C4) 资金动作隔离: payment-refund:direct 只允许 finance_manager 与 super_admin。预期 0 行。
SELECT 'C4-direct-refund' AS check_name, r.code, m.permission
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`permission`='zsjos:payment-refund:direct'
  AND r.`code` NOT IN ('finance_manager','super_admin');

-- C5) 后端强校验权限是否全部有了菜单行（仅统计未删除且启用的行）。预期 0 行。
SELECT 'C5-missing-menu' AS check_name, p.permission
FROM (
  SELECT 'zsjos:lead-detail:follow-up-read' permission UNION ALL
  SELECT 'zsjos:lead-detail:appeal-read' UNION ALL
  SELECT 'zsjos:lead-detail:complaint-read' UNION ALL
  SELECT 'zsjos:lead-detail:order-read' UNION ALL
  SELECT 'zsjos:lead-detail:flow-read' UNION ALL
  SELECT 'zsjos:sales-order:refund-apply' UNION ALL
  SELECT 'zsjos:payment-refund:read' UNION ALL
  SELECT 'zsjos:payment-refund:refresh' UNION ALL
  SELECT 'zsjos:payment-refund:direct' UNION ALL
  SELECT 'zsjos:student:exam-date-update'
) p
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` m
   WHERE m.`permission`=p.`permission` AND m.`deleted`=b'0' AND m.`status`=0);
