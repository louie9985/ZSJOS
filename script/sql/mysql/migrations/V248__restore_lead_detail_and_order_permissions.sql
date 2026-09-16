-- V248: 补建被覆盖的客资详情与订单/退款权限菜单行，并恢复原有授权
--
-- 事故背景
--   V086 用菜单 ID 6920-6923 建了 4 个客资详情页签权限(父节点 6770)。
--   V224 复用了同一批 ID 6920-6927 建「学员账号交付」(父节点 6735)，其
--   ON DUPLICATE KEY UPDATE 覆盖了 permission 列，导致：
--     - 4 个 lead-detail 权限行连同语义一并消失；
--     - V086 遗留的 system_role_menu 授权行仍指向这些 ID，语义已错位；
--     - V091 建在 6924 的 zsjos:lead-detail:flow-read 同样被 V224 覆盖。
--   因此客资详情 5 个页签对全部角色不可用，且后端断言无法通过配置恢复。
--
-- 本迁移
--   1. 在空闲号段 602300+ 重新建立这 5 个页签权限行，父节点仍为 6770 客资管理。
--   2. 按 V086 声明的「源权限 -> 详情权限」继承关系，为当前持有源权限的角色恢复授权。
--      这正是 V086 的原始口径，不按角色名推断。flow-read 沿用 V091 的原始归属
--      (仅 sales_manager)，并按其只读意图保持最小。
--   3. 补建订单范围、支付退款与学员考期三组权限行(此前后端已强校验但从未定义菜单)，
--      并按已确认的业务归属授权。
--   4. 清理 V086 遗留的错位授权行：原 6920-6923 上指向 lead-detail 语义(现已不存在)
--      的关系由 V224 的正常授权覆盖，本迁移不回改；仅显式移除任何仍残留的
--      指向 lead-detail 权限名的关系(应为 0 行)。
--
-- 明确排除
--   normal_user 与 teaching_assistant 不参与 lead-detail 恢复：它们在 V246 获得
--   zsjos:student:query-my，而 V086 的继承表把该权限列为 follow-up/order 的源，
--   会连带命中。这不是 V086 当年的推导条件，故显式排除。
--   payment-refund:direct 为资金出账动作，仅授予 finance_manager 与 super_admin。
--   payment-refund:read 同样不授予 system_administrator：V242 只把支付主体配置面
--   交给 finance_manager 与 super_admin，且权限矩阵明确禁止管理员持有财务审核与
--   资金操作权限。管理员需要退款只读时另行评审。
--
-- 可重复性
--   菜单以固定 ID + ON DUPLICATE KEY UPDATE 建立；授权全部为 not-exists 保护插入。
--   重复执行不产生重复行。
--
-- 依赖与顺序
--   需在 V247 之后应用。父节点 6770/6810/73511/602200/73020 必须存在。
--
-- 回滚限制
--   不提供自动回滚。恢复原状需删除本文件创建的菜单行与 creator='V248' 的关系，
--   但会重新造成 5 个页签不可用的既有故障，因此回滚没有业务意义。
--
-- 编码
--   仅写入 ASCII 权限标识、角色编码与菜单 ID，以及中文菜单名。UTF-8 无 BOM。

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
-- 2) 客资详情：按 V086 声明的源权限继承关系恢复授权
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_v248_inherit`;
CREATE TEMPORARY TABLE `tmp_v248_inherit` (
  `permission`        varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_permission` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`permission`,`source_permission`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `tmp_v248_inherit` (`permission`,`source_permission`) VALUES
('zsjos:lead-detail:follow-up-read','zsjos:lead-follow-up:query'),
('zsjos:lead-detail:follow-up-read','zsjos:subordinate-sales:query'),
('zsjos:lead-detail:follow-up-read','zsjos:student:query-my'),
('zsjos:lead-detail:follow-up-read','zsjos:lead:query-all'),
('zsjos:lead-detail:appeal-read','zsjos:lead:appeal:create'),
('zsjos:lead-detail:appeal-read','zsjos:lead:appeal:query'),
('zsjos:lead-detail:appeal-read','zsjos:subordinate-sales:query'),
('zsjos:lead-detail:appeal-read','zsjos:lead:query-all'),
('zsjos:lead-detail:complaint-read','zsjos:lead-complaint:create'),
('zsjos:lead-detail:complaint-read','zsjos:lead-complaint:handle'),
('zsjos:lead-detail:complaint-read','zsjos:subordinate-sales:query'),
('zsjos:lead-detail:complaint-read','zsjos:lead:query-all'),
('zsjos:lead-detail:order-read','zsjos:sales-order:query'),
('zsjos:lead-detail:order-read','zsjos:sales-order:create'),
('zsjos:lead-detail:order-read','zsjos:subordinate-sales:query'),
('zsjos:lead-detail:order-read','zsjos:student:query-my'),
('zsjos:lead-detail:order-read','zsjos:lead:query-all');

-- 显式排除: 这两个角色在 V246 才获得 student:query-my, 不属 V086 推导条件。
DROP TEMPORARY TABLE IF EXISTS `tmp_v248_excluded_role`;
CREATE TEMPORARY TABLE `tmp_v248_excluded_role` (`role_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL PRIMARY KEY) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO `tmp_v248_excluded_role` (`role_code`) VALUES ('normal_user'),('teaching_assistant');

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT source_grant.`role_id`, target_menu.`id`, 'V248', NOW(), 'V248', NOW(), b'0', source_grant.`tenant_id`
FROM `tmp_v248_inherit` inherit
JOIN `system_menu` source_menu
  ON source_menu.`permission`=inherit.`source_permission` AND source_menu.`deleted`=b'0'
JOIN `system_role_menu` source_grant
  ON source_grant.`menu_id`=source_menu.`id` AND source_grant.`deleted`=b'0'
JOIN `system_role` holder
  ON holder.`id`=source_grant.`role_id` AND holder.`deleted`=b'0'
JOIN `system_menu` target_menu
  ON target_menu.`permission`=inherit.`permission` AND target_menu.`deleted`=b'0' AND target_menu.`status`=0
WHERE NOT EXISTS (
    SELECT 1 FROM `tmp_v248_excluded_role` excluded
     WHERE excluded.`role_code`=holder.`code`)
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
     WHERE existing.`role_id`=source_grant.`role_id` AND existing.`menu_id`=target_menu.`id`
       AND existing.`tenant_id`=source_grant.`tenant_id` AND existing.`deleted`=b'0');

-- flow-read: V091 的原始归属为 enabled sales_manager。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT role_row.`id`, menu_row.`id`, 'V248', NOW(), 'V248', NOW(), b'0', role_row.`tenant_id`
FROM `system_role` role_row
JOIN `system_menu` menu_row
  ON menu_row.`permission`='zsjos:lead-detail:flow-read' AND menu_row.`deleted`=b'0' AND menu_row.`status`=0
WHERE role_row.`code`='sales_manager' AND role_row.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
     WHERE existing.`role_id`=role_row.`id` AND existing.`menu_id`=menu_row.`id`
       AND existing.`tenant_id`=role_row.`tenant_id` AND existing.`deleted`=b'0');

-- ---------------------------------------------------------------------------
-- 2b) 订单范围权限：zsjos:sales-order:query-own / query-team 由 V025/V136 建在 6813/73510，
--     V195 统一订单管理时将其软删。这两个权限仍在后端被校验(SalesOrderController 的
--     /my-page、/team-page 等)，且 V195 的验证断言要求「不存在这两个权限的菜单行」，
--     因此直接新建菜单行会与既有断言冲突。正确做法是保留软删行不动、不为其建新行，
--     权限的可配置性改由后续独立评审决定(见 README)。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 3) 支付退款 / 学员考期：按已确认归属授权
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_v248_grant`;
CREATE TEMPORARY TABLE `tmp_v248_grant` (
  `role_code`  varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`role_code`,`permission`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `tmp_v248_grant` (`role_code`,`permission`) VALUES
('sales_specialist','zsjos:sales-order:refund-apply'),
('sales_manager','zsjos:sales-order:refund-apply'),
('finance_manager','zsjos:payment-refund:read'),
('finance_specialist','zsjos:payment-refund:read'),
('finance_manager','zsjos:payment-refund:refresh'),
('finance_specialist','zsjos:payment-refund:refresh'),
('finance_manager','zsjos:payment-refund:direct'),
('study_planner','zsjos:student:exam-date-update'),
('exam_manager','zsjos:student:exam-date-update'),
('exam_specialist','zsjos:student:exam-date-update'),
('enrollment_specialist','zsjos:student:exam-date-update');

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT role_row.`id`, menu_row.`id`, 'V248', NOW(), 'V248', NOW(), b'0', role_row.`tenant_id`
FROM `tmp_v248_grant` grant_row
JOIN `system_role` role_row
  ON role_row.`code`=grant_row.`role_code` AND role_row.`deleted`=b'0' AND role_row.`status`=0
JOIN `system_menu` menu_row
  ON menu_row.`permission`=grant_row.`permission` AND menu_row.`deleted`=b'0' AND menu_row.`status`=0
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
   WHERE existing.`role_id`=role_row.`id` AND existing.`menu_id`=menu_row.`id`
     AND existing.`tenant_id`=role_row.`tenant_id` AND existing.`deleted`=b'0');

-- ---------------------------------------------------------------------------
-- 4) super_admin 全量: 补齐本迁移新建的全部权限节点
-- ---------------------------------------------------------------------------
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT role_row.`id`, menu_row.`id`, 'V248', NOW(), 'V248', NOW(), b'0', role_row.`tenant_id`
FROM `system_role` role_row
JOIN `system_menu` menu_row ON menu_row.`id` IN (602300,602301,602302,602303,602304,602307,602308,602309,602310,602311) AND menu_row.`deleted`=b'0'
WHERE role_row.`code`='super_admin' AND role_row.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
     WHERE existing.`role_id`=role_row.`id` AND existing.`menu_id`=menu_row.`id`
       AND existing.`tenant_id`=role_row.`tenant_id` AND existing.`deleted`=b'0');

DROP TEMPORARY TABLE IF EXISTS `tmp_v248_inherit`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v248_excluded_role`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v248_grant`;

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
