-- V252: 系统管理员补齐全部菜单授权
--
-- 目的
--   让 `system_administrator` 持有**全部启用菜单**。此前它只持有 232 项（V071 的 allowlist +
--   V246 补入的新落地配置页），缺失 2013 项：框架自带的系统管理/基础设施（用户、角色、菜单、
--   部门、岗位、字典、配置、公告、令牌、定时任务、表单构建、代码生成、API 接口…）以及本
--   项目多数 zsjos 业务菜单。
--
-- 背景
--   产品口径（2026-09-16）：**管理员拥有所有菜单权限**。此前"按需 allowlist"的做法导致
--   `system_administrator` 出现两类问题：
--     1) 界面不可达：它持有 `6741`/`79980`/`79990`（`parent_id=1` 系统管理根）却不持有菜单 1，
--        前端建树丢弃这三个节点；
--     2) 覆盖缺口：新增业务页面后需要人工判断是否补授，长期累积遗漏（本次实测缺 2013 项）。
--   本迁移把口径统一为"管理员 = 全量菜单"，此后新增菜单只需授予 `normal_user`（通用基线）
--   与 `system_administrator`/`super_admin`（全量），不再逐个业务角色判断。
--
-- 与既有约束的冲突（需产品确认）
--   本迁移**取代**了此前"禁止 `system_administrator` 持有财务复核与资金导出权限"的约束。
--   该约束来自 V071/V242/V246 与 `docs/architecture/zsjos-role-permission-matrix.md`，
--   并由 `verify-role-menu-coverage.sql` 的 violation 检查强制。全量授权与它不可兼得：
--   下列菜单在本迁移中一并授予 `system_administrator`（此前它已持有 `zsjos:export:lead`）：
--     `zsjos:sales-order:review`（处理成交订单审批）、`zsjos:cashback:finance-query`（返现管理）、
--     `zsjos:withdrawal:finance-query`（财务查询提现）、`zsjos:withdrawal:review`（提现审核）、
--     `zsjos:withdrawal:payout`（记录打款）、`zsjos:export:order`、`zsjos:export:finance-order`、
--     `zsjos:export:cashback`、`zsjos:export:withdrawal`、`zsjos:export:lead`。
--   同步调整：`verify-role-menu-coverage.sql` 的 violation 检查移除 `system_administrator` 条款。
--
-- 口径
--   "全部启用菜单" = `system_menu` 中 `deleted=b'0' AND status=0` 的全部节点（目录/页面/按钮）。
--   禁用菜单（`status=1`，如工作计划模块）不授予，与 `super_admin` 的框架行为一致
--   （`PermissionServiceImpl.getRoleMenuListByRoleId` 对超管返回 `menuService.getMenuList()` 全量，
--   `getPermissionInfo` 随后 `filterDisableMenus` 过滤禁用项）。
--
-- 可重复性
--   纯 INSERT ... WHERE NOT EXISTS；重复执行不产生新行。
--
-- 依赖与顺序
--   需先应用至 V251。应在 backend 部署前或同批次应用。
--
-- 回滚限制
--   本迁移不提供自动回滚。回滚需精确删除 `creator='V252'` 的 `system_role_menu` 行。
--
-- 编码
--   源文件与客户端连接均为 utf8mb4；仅写入 ASCII 权限标识、角色编码与菜单 ID。

SET NAMES utf8mb4;

-- =====================================================================================
-- 1) 授予 system_administrator 全部启用菜单（含禁用项的父级不单独处理：禁用项整链不授）
-- =====================================================================================

DROP TEMPORARY TABLE IF EXISTS `tmp_v252_all_menu`;
CREATE TEMPORARY TABLE `tmp_v252_all_menu` (
  `menu_id` bigint NOT NULL PRIMARY KEY
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4;

INSERT INTO `tmp_v252_all_menu` (`menu_id`)
SELECT `id` FROM `system_menu` WHERE `deleted`=b'0' AND `status`=0
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);

DROP TEMPORARY TABLE IF EXISTS `tmp_v252_admin`;
CREATE TEMPORARY TABLE `tmp_v252_admin` (
  `role_id` bigint NOT NULL PRIMARY KEY,
  `tenant_id` bigint NOT NULL
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4;

INSERT INTO `tmp_v252_admin` (`role_id`,`tenant_id`)
SELECT `id`, `tenant_id` FROM `system_role`
WHERE `code`='system_administrator' AND `deleted`=b'0'
ON DUPLICATE KEY UPDATE `role_id`=VALUES(`role_id`);

-- 预检：输出目标角色的授权现状（供人工核对）
SELECT 'V252-precheck' AS check_name,
       (SELECT COUNT(*) FROM `tmp_v252_all_menu`) AS enabled_menu_count,
       (SELECT COUNT(*) FROM `system_role_menu` rm JOIN `tmp_v252_admin` a ON a.`role_id`=rm.`role_id`
         WHERE rm.`deleted`=b'0') AS grants_before;

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT a.`role_id`, t.`menu_id`, 'V252', NOW(), 'V252', NOW(), b'0', a.`tenant_id`
FROM `tmp_v252_admin` a
JOIN `tmp_v252_all_menu` t
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` rm
   WHERE rm.`role_id`=a.`role_id` AND rm.`menu_id`=t.`menu_id`
     AND rm.`tenant_id`=a.`tenant_id` AND rm.`deleted`=b'0');

-- =====================================================================================
-- 2) 只读核对（不修改数据）
-- =====================================================================================

-- C1) 仍未授权的启用菜单数。预期 0。
SELECT 'C1-missing' AS check_name, COUNT(*) AS missing_enabled_menus
FROM `tmp_v252_all_menu` t
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role_menu` rm JOIN `tmp_v252_admin` a ON a.`role_id`=rm.`role_id`
   WHERE rm.`menu_id`=t.`menu_id` AND rm.`deleted`=b'0');

-- C2) 授权总数。预期 = 启用菜单总数。
SELECT 'C2-total' AS check_name,
       (SELECT COUNT(*) FROM `system_role_menu` rm JOIN `tmp_v252_admin` a ON a.`role_id`=rm.`role_id`
         WHERE rm.`deleted`=b'0') AS grants_after,
       (SELECT COUNT(*) FROM `tmp_v252_all_menu`) AS enabled_menu_count;

-- C3) 悬空授权。预期 0 行。
SELECT 'C3-orphan-grant' AS check_name, m.`id` AS menu_id, m.`name`, m.`parent_id`
FROM `system_role_menu` rm
JOIN `tmp_v252_admin` a ON a.`role_id`=rm.`role_id`
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.`role_id`=rm.`role_id` AND prm.`menu_id`=m.`parent_id`
       AND prm.`tenant_id`=rm.`tenant_id` AND prm.`deleted`=b'0')
LIMIT 50;

DROP TEMPORARY TABLE IF EXISTS `tmp_v252_all_menu`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v252_admin`;

-- 版本记录
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V252','系统管理员补齐全部菜单授权', SHA2('V252__system_administrator_full_menu.sql',256), NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V252','系统管理员补齐全部菜单授权', SHA2('V252__system_administrator_full_menu.sql',256), '2026.09.16-170000-v252', NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
