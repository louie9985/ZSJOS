-- V251: 通用菜单权限抽离到普通员工角色
--
-- 目的
--   把"每个员工都需要"的通用工作台菜单（工作台首页、日历、工单中心、需求与反馈、
--   学员账号交付、我的资产/采购申请等）从 33 个角色各自重复持有，收敛为
--   普通员工(normal_user)这一"基础角色"统一持有；其余 32 个业务角色只保留
--   其岗位专属的业务功能授权。
--
-- 背景
--   截至 V250，tenant 1 的 33 个角色全部各自持有一组完全相同的 45 个菜单
--   （3 个目录 + 10 个页面 + 32 个按钮），共 33 x 45 = 1485 行重复授权。
--   新增一个通用菜单需要同步改 33 个角色，属于结构性的维护负担，
--   且违反"角色 = 岗位所需业务功能"的建模意图。
--
-- 口径来源
--   docs/architecture/zsjos-role-permission-matrix.md（角色-权限矩阵）
--   AGENTS.md 可配置权限契约：授权按 system_role.code + system_menu.permission 解析，
--   不按角色显示名/岗位名/部门名/菜单 ID。
--
-- 设计
--   授权是并集语义：一个用户最终可见菜单 = 其全部角色的授权并集
--   （PermissionServiceImpl.getRoleMenuListByRoleId 对多角色取并集）。
--   因此把通用菜单从业务角色移到 normal_user，只要账号同时持有 normal_user，
--   最终可见菜单的**并集**不变。
--
--   为什么用 normal_user 而不是新建一个基础角色：
--   - normal_user 已经是既有的"普通员工"角色，V246 已授予其客资/学员/工单/资产/
--     公告/HR 员工端/BPM/消息中心/素材浏览等全部员工自助能力，本身就是事实上的基础角色；
--   - tenant 1 的账号中 41 个已持有 normal_user，收敛不改变任何现存有角色账号的可见范围。
--
--   为什么还要"祖先回补"（第 3 步）：
--   通用集里含两个**纯容器目录**：`6735 工作台` 与 `73600 日历`。业务角色的业务页面
--   （客资管理、订单管理、班级管理、素材库等）都挂在 6735 下。系统只返回被直接授权的
--   菜单，前端建树时父节点缺失的子节点会被丢弃——把 6735 一并撤销会让这些业务页面
--   **权限仍在但界面不可达**（并集比对看不出这种退化，只有悬空授权检查能发现）。
--   因此撤销后必须为"仍有子菜单被授权的祖先目录"回补授权，且仅回补祖先目录本身。
--
-- 语义
--   1) 计算通用集（tenant 1 全部启用角色的共同持有菜单）；
--   2) 补齐 normal_user 对通用集的持有；
--   3) 从业务角色（normal_user、super_admin 除外）撤销通用集授权；
--   4) 自底向上回补：任何"仍持有被授权子孙菜单"的祖先目录，补回该角色的授权。
--   super_admin 不撤销：其授权不受影响（PermissionServiceImpl 对超管直接返回全量菜单），
--   保留其行可避免与"超管显式全量授权"的既有状态不一致。
--
--   通用集口径不是写死的 45 个 ID，而是运行时"tenant 1 全部启用角色共同持有的菜单"。
--
-- 可重复性
--   补齐/回补为 INSERT ... WHERE NOT EXISTS；撤销按 (role_id, menu_id) 精确 UPDATE deleted，
--   重复执行不产生新变化。
--
-- 安全前置
--   若某账号持有业务角色但未持有 normal_user，收敛会缩小其可见范围。本迁移在撤销前
--   校验 tenant 1 内不存在此类账号（super_admin 账号除外），存在则整批中止不生效。
--
-- 依赖与顺序
--   需先应用至 V250。应在 backend 部署前或同批次应用。
--
-- 回滚限制
--   本迁移不提供自动回滚。回滚需按 creator='V251' 精确删除新增行，
--   并将 updater='V251' 的行 deleted 复位为 b'0'；禁止按 menu_id 范围批量恢复。
--
-- 编码
--   源文件与客户端连接均为 utf8mb4；仅写入 ASCII 权限标识、角色编码与菜单 ID。

SET NAMES utf8mb4;

-- =====================================================================================
-- 1) 计算"通用菜单集"（应唯一归属 normal_user 的菜单）
-- =====================================================================================

DROP TEMPORARY TABLE IF EXISTS `tmp_v251_target`;
CREATE TEMPORARY TABLE `tmp_v251_target` (
  `menu_id` bigint NOT NULL PRIMARY KEY
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4;

-- 全体启用角色的共同持有菜单（tenant 1）
INSERT INTO `tmp_v251_target` (`menu_id`)
SELECT rm.`menu_id`
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id`
  AND r.`deleted`=b'0' AND r.`tenant_id`=1
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND rm.`tenant_id`=1
GROUP BY rm.`menu_id`
HAVING COUNT(DISTINCT rm.`role_id`) = (
  SELECT COUNT(*) FROM `system_role` WHERE `deleted`=b'0' AND `tenant_id`=1)
ON DUPLICATE KEY UPDATE `menu_id`=VALUES(`menu_id`);

-- =====================================================================================
-- 2) 安全前置：任何持有业务角色但未持有 normal_user 的启用账号会使收敛缩小其可见范围
-- =====================================================================================

DROP TEMPORARY TABLE IF EXISTS `tmp_v251_unsafe_users`;
CREATE TEMPORARY TABLE `tmp_v251_unsafe_users` (
  `user_id` bigint NOT NULL PRIMARY KEY
) ENGINE=MEMORY DEFAULT CHARSET=utf8mb4;

INSERT INTO `tmp_v251_unsafe_users` (`user_id`)
SELECT ur.`user_id`
FROM `system_user_role` ur
JOIN `system_role` r ON r.`id`=ur.`role_id` AND r.`deleted`=b'0' AND r.`tenant_id`=1
WHERE ur.`deleted`=b'0' AND ur.`user_id`<>1  -- 账号 1 为 super_admin，超管不受菜单授权限制
GROUP BY ur.`user_id`
HAVING SUM(r.`code`='normal_user') = 0;

-- 先输出预检结果供人工/自动化核对（预期 unsafe_user_count = 0）
SET @v251_unsafe := (SELECT COUNT(*) FROM `tmp_v251_unsafe_users`);
SELECT 'V251-precheck' AS check_name, @v251_unsafe AS unsafe_user_count;

-- 中止守卫：预检未通过时，动态 SQL 引用不存在的列，MySQL 抛错并使 mysql 客户端
-- 终止后续语句，整批不生效；预检通过时该语句无副作用。
SET @v251_guard := IF(@v251_unsafe = 0,
  'SELECT ''V251 precheck passed'' AS v251_precheck_result',
  'SELECT v251_aborted_users_hold_business_roles_without_normal_user FROM v251_guard');
PREPARE v251_stmt FROM @v251_guard;
EXECUTE v251_stmt;
DEALLOCATE PREPARE v251_stmt;

-- =====================================================================================
-- 3) 补齐 normal_user 对通用集的持有
-- =====================================================================================

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, t.`menu_id`, 'V251', NOW(), 'V251', NOW(), b'0', 1
FROM `system_role` r
JOIN `tmp_v251_target` t
WHERE r.`code`='normal_user' AND r.`deleted`=b'0' AND r.`tenant_id`=1
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
     WHERE rm.`role_id`=r.`id` AND rm.`menu_id`=t.`menu_id`
       AND rm.`tenant_id`=1 AND rm.`deleted`=b'0');

-- =====================================================================================
-- 4) 从业务角色撤销通用集授权（normal_user / super_admin 除外）
-- =====================================================================================

UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0' AND r.`tenant_id`=1
JOIN `tmp_v251_target` t ON t.`menu_id`=rm.`menu_id`
SET rm.`deleted`=b'1', rm.`updater`='V251', rm.`update_time`=NOW()
WHERE rm.`deleted`=b'0' AND rm.`tenant_id`=1
  AND r.`code` NOT IN ('normal_user','super_admin');

-- =====================================================================================
-- 5) 祖先回补：仍被授权菜单的祖先必须保持授权，否则子页面不可达
-- =====================================================================================

-- 规则：任何已授权菜单，其父节点必须在该角色下同样授权。重复执行直到无新增，
-- 即可闭合整条祖先链（不限类型，覆盖"页面挂在页面下"的结构，如 73611 管理考期
-- 挂在 73610 考期日历 下，而 73610 的父目录 73600 日历属于通用集）。
-- 迭代 5 次足以覆盖本项目最深菜单层级。
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`parent_id`, 'V251', NOW(), 'V251', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.`role_id`=rm.`role_id` AND prm.`menu_id`=m.`parent_id`
       AND prm.`tenant_id`=rm.`tenant_id` AND prm.`deleted`=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`parent_id`, 'V251', NOW(), 'V251', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.`role_id`=rm.`role_id` AND prm.`menu_id`=m.`parent_id`
       AND prm.`tenant_id`=rm.`tenant_id` AND prm.`deleted`=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`parent_id`, 'V251', NOW(), 'V251', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.`role_id`=rm.`role_id` AND prm.`menu_id`=m.`parent_id`
       AND prm.`tenant_id`=rm.`tenant_id` AND prm.`deleted`=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`parent_id`, 'V251', NOW(), 'V251', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.`role_id`=rm.`role_id` AND prm.`menu_id`=m.`parent_id`
       AND prm.`tenant_id`=rm.`tenant_id` AND prm.`deleted`=b'0');

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT rm.`role_id`, m.`parent_id`, 'V251', NOW(), 'V251', NOW(), b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.`role_id`=rm.`role_id` AND prm.`menu_id`=m.`parent_id`
       AND prm.`tenant_id`=rm.`tenant_id` AND prm.`deleted`=b'0');


-- =====================================================================================
-- 6) 只读核对（不修改数据）
-- =====================================================================================

-- C1) 仍直接持有通用菜单的业务角色。预期只剩 super_admin 一行（45 项）。
SELECT 'C1-universal-left' AS check_name, r.`code` AS role_code, r.`name` AS role_name,
       COUNT(*) AS universal_grants
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0' AND r.`tenant_id`=1
JOIN `tmp_v251_target` t ON t.`menu_id`=rm.`menu_id`
WHERE rm.`deleted`=b'0' AND rm.`tenant_id`=1
GROUP BY r.`id`, r.`code`, r.`name`
ORDER BY universal_grants DESC, r.`id`;

-- C2) 通用集合计（供人工确认 45 项量级）。
SELECT 'C2-universal-count' AS check_name, COUNT(*) AS universal_menu_count
FROM `tmp_v251_target`;

-- C3) 悬空授权：被授权菜单的祖先未授权。应用后预期 0 行。
SELECT 'C3-orphan-grant' AS check_name, r.`code` AS role_code, m.`id` AS menu_id,
       m.`name`, m.`parent_id`
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND rm.`tenant_id`=r.`tenant_id` AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.`role_id`=rm.`role_id` AND prm.`menu_id`=m.`parent_id`
       AND prm.`tenant_id`=rm.`tenant_id` AND prm.`deleted`=b'0')
ORDER BY r.`code`, m.`parent_id`, m.`id`
LIMIT 200;

-- C4) 持有业务角色但未持有 normal_user 的账号。预期 0 行。
SELECT 'C4-user-coverage' AS check_name, ur.`user_id`
FROM `system_user_role` ur
JOIN `system_role` r ON r.`id`=ur.`role_id` AND r.`deleted`=b'0' AND r.`tenant_id`=1
WHERE ur.`deleted`=b'0' AND ur.`user_id`<>1
GROUP BY ur.`user_id`
HAVING SUM(r.`code`='normal_user')=0;

DROP TEMPORARY TABLE IF EXISTS `tmp_v251_target`;
DROP TEMPORARY TABLE IF EXISTS `tmp_v251_unsafe_users`;

-- 版本记录
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V251','通用菜单权限抽离到普通员工角色', SHA2('V251__universal_menu_baseline.sql',256), NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V251','通用菜单权限抽离到普通员工角色', SHA2('V251__universal_menu_baseline.sql',256), '2026.09.16-090000-v251', NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
