-- V246 只读验证脚本。可在应用 V246 之后随时重复执行，不修改任何数据。
-- 用法:
--   mysql --default-character-set=utf8mb4 -u USER -p DATABASE < verify-role-menu-coverage.sql
SET NAMES utf8mb4;

-- 1) 仍无任何角色授权的启用 ZSJOS 权限菜单。应用 V246 后预期为 0 行。
SELECT 'ungranted-zsjos-menu' AS check_name, m.id AS menu_id, m.permission, m.name, m.type
FROM `system_menu` m
WHERE m.deleted=b'0' AND m.status=0 AND m.permission LIKE 'zsjos:%'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
     WHERE rm.menu_id=m.id AND rm.deleted=b'0')
ORDER BY m.type, m.id;

-- 2) 口径越权项复核。预期 0 行。
--    依据 docs/architecture/zsjos-role-permission-matrix.md 的明确禁止条款。
SELECT 'violation' AS check_name, r.code AS role_code, m.permission, m.name
FROM `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.deleted=b'0'
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0'
WHERE rm.deleted=b'0'
  AND (
    (r.code IN ('sales_manager','sales_specialist') AND m.permission IN (
       'zsjos:lead:query-all','zsjos:sales-order:review','zsjos:withdrawal:review',
       'zsjos:withdrawal:payout','zsjos:cashback:finance-query','zsjos:withdrawal:finance-query'))
    OR (r.code IN ('finance_manager','finance_specialist') AND m.permission='zsjos:export:lead')
    OR (r.code IN ('enrollment_manager','enrollment_specialist') AND m.permission IN (
       'zsjos:cashback:finance-query','zsjos:withdrawal:finance-query','zsjos:withdrawal:review',
       'zsjos:withdrawal:payout','zsjos:export:cashback','zsjos:export:withdrawal',
       'zsjos:export:order','zsjos:export:finance-order'))
    -- V252 起 system_administrator 为「管理员 = 全量菜单」，不再受此约束（产品口径 2026-09-16）
    OR (r.code='boss' AND m.permission IN (
       'zsjos:cashback:finance-query','zsjos:withdrawal:finance-query','zsjos:withdrawal:review',
       'zsjos:withdrawal:payout','zsjos:export:lead','zsjos:export:order',
       'zsjos:export:cashback','zsjos:export:withdrawal','zsjos:export:finance-order'))
  )
ORDER BY r.code, m.permission;

-- 3) 每个启用角色的 ZSJOS 权限菜单计数, 用于人工核对职责范围。
SELECT r.id AS role_id, r.code AS role_code, r.name AS role_name,
       COUNT(DISTINCT m.id) AS zsjos_menu_count,
       SUM(CASE WHEN m.type=2 THEN 1 ELSE 0 END) AS page_count,
       SUM(CASE WHEN m.type=3 THEN 1 ELSE 0 END) AS button_count
FROM `system_role` r
LEFT JOIN `system_role_menu` rm ON rm.role_id=r.id AND rm.deleted=b'0'
LEFT JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.permission LIKE 'zsjos:%'
WHERE r.deleted=b'0' AND r.tenant_id=1
GROUP BY r.id, r.code, r.name
ORDER BY zsjos_menu_count DESC, r.id;

-- 4) 菜单树完整性: 被授权菜单的父节点是否也被同一角色授权(避免出现悬空节点)。
--    应用 V249/V250 后预期为 0 行。非空表示某按钮的界面入口不可达。
--    仅统计"父节点本身是启用菜单"的授权行: 父节点已停用(status=1, 如支付管理/公众号管理/
--    商城/CRM/ERP/AI/IoT/MES/WMS 等框架停用模块)时, 其后代即使被授权也不会渲染, 属预期状态,
--    由 4a) 单独列出。系统的过滤规则是 filterDisableMenus(仅按自身 status), 因此
--    "父停用 + 子启用"是历史遗留数据, 不是本迁移引入, 也不影响界面。
SELECT 'orphan-grant' AS check_name, r.code AS role_code, m.id AS menu_id, m.name, m.parent_id
FROM `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.deleted=b'0'
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.status=0
JOIN `system_menu` p ON p.id=m.parent_id AND p.deleted=b'0' AND p.status=0
WHERE rm.deleted=b'0' AND rm.tenant_id=r.tenant_id AND m.parent_id>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` prm
     WHERE prm.role_id=rm.role_id AND prm.menu_id=m.parent_id
       AND prm.tenant_id=rm.tenant_id AND prm.deleted=b'0')
ORDER BY r.code, m.parent_id, m.id
LIMIT 200;

-- 4a) 父节点已停用的授权行(仅提示, 非缺陷)。这些菜单不会出现在界面上。
SELECT 'grant-under-disabled-parent' AS check_name, r.code AS role_code, m.id AS menu_id,
       m.name, m.parent_id, p.name AS parent_name
FROM `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.deleted=b'0'
JOIN `system_menu` m ON m.id=rm.menu_id AND m.deleted=b'0' AND m.status=0
JOIN `system_menu` p ON p.id=m.parent_id AND p.deleted=b'0' AND p.status=1
WHERE rm.deleted=b'0' AND m.parent_id>2
ORDER BY r.code, m.parent_id, m.id
LIMIT 50;

-- 4b) 跨租户授权: 关系租户必须等于角色租户。预期 0 行。
SELECT 'cross-tenant-grant' AS check_name, rm.id, rm.tenant_id AS grant_tenant,
       r.tenant_id AS role_tenant, r.code, rm.menu_id
FROM `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.deleted=b'0'
WHERE rm.deleted=b'0' AND rm.tenant_id<>r.tenant_id;

-- 4c) 重复授权: 同租户/角色/菜单不得有多条有效关系。预期 0 行。
SELECT 'duplicate-grant' AS check_name, tenant_id, role_id, menu_id, COUNT(*) AS rows_found
FROM `system_role_menu` WHERE deleted=b'0'
GROUP BY tenant_id, role_id, menu_id HAVING COUNT(*)>1;

-- 5) V246 版本登记确认。预期各返回 1 行。
SELECT 'version' AS check_name, module_code, version, description
FROM `zsjos_module_schema_version` WHERE version='V246';

-- 6) 兼职端 System 角色应已由 V247 退役, 且不持有任何菜单授权。预期 active_role=0, grants=0。
SELECT 'partner-role-retired' AS check_name,
       (SELECT COUNT(*) FROM `system_role` WHERE `code`='part_time_partner' AND `deleted`=b'0') AS active_role,
       (SELECT COUNT(*) FROM `system_role_menu` rm JOIN `system_role` r ON r.`id`=rm.`role_id`
          WHERE r.`code`='part_time_partner' AND rm.`deleted`=b'0') AS grants;

-- 7) V251 通用菜单基线: 通用集(tenant 1 全部启用角色的共同持有菜单)只应由 normal_user
--    与 super_admin 持有。预期 0 行 —— 任何业务角色出现在结果里都说明收敛被回退。
SELECT 'universal-menu-on-business-role' AS check_name, r.code AS role_code,
       r.name AS role_name, COUNT(*) AS universal_grants
FROM `system_role_menu` rm
JOIN `system_role` r ON r.id=rm.role_id AND r.deleted=b'0' AND r.tenant_id=1
WHERE rm.deleted=b'0' AND rm.tenant_id=1
  AND r.code NOT IN ('normal_user','super_admin')
  AND rm.menu_id IN (
    SELECT rm2.menu_id
    FROM `system_role_menu` rm2
    JOIN `system_role` r2 ON r2.id=rm2.role_id AND r2.deleted=b'0' AND r2.tenant_id=1
    WHERE rm2.deleted=b'0' AND rm2.tenant_id=1
    GROUP BY rm2.menu_id
    HAVING COUNT(DISTINCT rm2.role_id) = (
      SELECT COUNT(*) FROM `system_role` WHERE deleted=b'0' AND tenant_id=1))
GROUP BY r.id, r.code, r.name
ORDER BY universal_grants DESC, r.id;

-- 7b) 持有业务角色但未持有 normal_user 的账号: 收敛会缩小其可见范围, 应为 0。
SELECT 'user-without-normal-user' AS check_name, ur.user_id, u.username, u.nickname
FROM `system_user_role` ur
JOIN `system_users` u ON u.id=ur.user_id AND u.deleted=b'0'
WHERE ur.deleted=b'0' AND ur.user_id<>1
  AND NOT EXISTS (
    SELECT 1 FROM `system_user_role` nur_ur
    JOIN `system_role` nur_r ON nur_r.id=nur_ur.role_id AND nur_r.deleted=b'0'
    WHERE nur_ur.deleted=b'0' AND nur_ur.user_id=ur.user_id AND nur_r.code='normal_user')
GROUP BY ur.user_id, u.username, u.nickname;

-- 7c) V251 版本登记确认。预期 1 行。
SELECT 'version' AS check_name, module_code, version, description
FROM `zsjos_module_schema_version` WHERE version='V251';

-- 8) V252 版本登记确认。预期 1 行。
SELECT 'version' AS check_name, module_code, version, description
FROM `zsjos_module_schema_version` WHERE version='V252';
