-- V249: 补齐 BPM 与站内信管理按钮的缺失父级授权（修复悬空授权）
--
-- 问题
--   V109 给 dept_manager 授了 6 个 BPM 按钮（bpm:model:create/update/deploy/import、
--   bpm:category:query/create），V071 给 system_administrator 授了业务通知规则与通知渠道按钮。
--   但它们的父级页面/目录没有被授权。系统返回的菜单集合只包含「被直接授权的菜单」，
--   前端建树时父节点缺失的子节点会被丢弃，因此这些按钮在界面上不可达 ——
--   权限标识本身有效（@PreAuthorize 可通过），但用户找不到入口。
--
--   受影响:
--     dept_manager         1195/1197/1199/602117 挂在 1193 流程模型下
--                          2715/2716 挂在 2714 流程分类下
--     system_administrator 6786-6789 挂在 6785 业务通知规则下
--                          602131/602132 挂在 602130 通知渠道下
--   这些父级(1193/2714/6785/602130)本身能到达根，但中间目录 1186 流程管理 与
--   2144 站内信管理 从未授权给这些角色。
--
-- 口径
--   按现有惯例精确保授权路径上的节点，不扩散到同级其他页面
--   （对照: normal_user 只持有 1185 工作流程 + 1200 审批中心，未持有 1186 流程管理）。
--   本迁移只新增缺失的父级关系，不修改任何既有授权，不删除任何数据。
--
-- 影响
--   授权后 dept_manager 将在「工作流程」下看到「流程管理」分组及其页面，可进入流程模型
--   与流程分类页面并使用 V109 已授予的按钮；system_administrator 将在「消息中心 → 站内信管理」
--   下看到业务通知规则与通知渠道页面。页面本身不含权限标识，实际可操作性仍由各自的按钮权限决定。
--
-- 可重复性
--   全部为 not-exists 保护的插入，重复执行不产生重复关系。
--
-- 依赖与顺序
--   需在 V248 之后应用。父级 1185/1186/1193/2714/2739/2144 必须存在。
--
-- 回滚限制
--   不提供自动回滚。撤销需按 (tenant_id, role_id, menu_id) 精确删除 creator='V249' 的关系。
--
-- 编码
--   仅写入 ASCII 角色编码与菜单 ID 及中文注释，UTF-8 无 BOM。

SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS `tmp_v249_ancestor_grant`;
CREATE TEMPORARY TABLE `tmp_v249_ancestor_grant` (
  `role_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `menu_id`   bigint NOT NULL,
  `reason`    varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`role_code`,`menu_id`)
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- dept_manager: V109 的 BPM 流程模型/流程分类发布能力需要完整父级路径
-- (1185 工作流程 -> 1186 流程管理 -> 1193 流程模型 / 2714 流程分类)
INSERT INTO `tmp_v249_ancestor_grant` (`role_code`,`menu_id`,`reason`) VALUES
('dept_manager',1185,'V109 BPM root'),
('dept_manager',1186,'V109 BPM management group'),
('dept_manager',1193,'V109 BPM model page'),
('dept_manager',2714,'V109 BPM category page'),
-- system_administrator: V071 通知规则/通知渠道按钮需要父级路径
-- (2739 消息中心 -> 2144 站内信管理 -> 6785 业务通知规则 / 602130 通知渠道)
('system_administrator',2739,'V071 notify center root'),
('system_administrator',2144,'V071 notify management group'),
('system_administrator',6785,'V071 notify rule page'),
('system_administrator',602130,'V071 notify channel page');

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT role_row.`id`, grant_row.`menu_id`, 'V249', NOW(), 'V249', NOW(), b'0', role_row.`tenant_id`
FROM `tmp_v249_ancestor_grant` grant_row
JOIN `system_role` role_row
  ON role_row.`code`=grant_row.`role_code` AND role_row.`deleted`=b'0' AND role_row.`status`=0
JOIN `system_menu` menu_row
  ON menu_row.`id`=grant_row.`menu_id` AND menu_row.`deleted`=b'0' AND menu_row.`status`=0
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` existing
   WHERE existing.`role_id`=role_row.`id` AND existing.`menu_id`=grant_row.`menu_id`
     AND existing.`tenant_id`=role_row.`tenant_id` AND existing.`deleted`=b'0');

DROP TEMPORARY TABLE IF EXISTS `tmp_v249_ancestor_grant`;

-- 版本记录
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V249','补齐 BPM 与站内信管理的缺失父级授权', SHA2('V249__repair_orphan_ancestor_grants.sql',256), NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V249','补齐 BPM 与站内信管理的缺失父级授权', SHA2('V249__repair_orphan_ancestor_grants.sql',256), '2026.09.15-210000-v249', NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

-- =====================================================================================
-- 只读核对
-- =====================================================================================

-- R1) 悬空授权是否已消除。预期 0 行（排除无父节点的根级按钮 parent_id<=2）。
SELECT 'R1-orphan-grant' AS check_name, r.code AS role_code, m.id AS menu_id, m.name, m.parent_id
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` parent_grant
     WHERE parent_grant.`role_id`=rm.`role_id` AND parent_grant.`menu_id`=m.`parent_id`
       AND parent_grant.`tenant_id`=rm.`tenant_id` AND parent_grant.`deleted`=b'0');

-- R2) 确认只新增了预期的 8 条关系。预期 rows=8。
SELECT 'R2-added' AS check_name, COUNT(*) AS rows_added
FROM `system_role_menu` WHERE `creator`='V249' AND `deleted`=b'0';
