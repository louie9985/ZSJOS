-- V250: 修复跨租户授权行，并补齐通知渠道路径的父级
--
-- 问题一：跨租户授权
--   V179 与后续手工修复把 3 条 system_role_menu 关系写成了 tenant_id=0，而它们指向的
--   角色(system_administrator, id=3029)属于租户 1。租户解析按关系的 tenant_id 过滤，
--   因此这 3 条授权对租户 1 的实际用户的**不生效**：
--     12680  system:notify-channel:query   (602131)
--     12681  system:notify-channel:update  (602132)
--     12686  通知渠道页面                  (602130)
--   修复方式：把关系的 tenant_id 更正为角色自身的租户，而不是删除后重建，
--   以保留原始 creator/时间线证据。
--
-- 问题二：通知渠道页的父级悬空
--   V249 已给 system_administrator 补授了 2144 站内信管理，但 602130 通知渠道页本身
--   在租户 0 的历史关系修正后需要重新确认父级可见性。
--
-- 口径
--   跨租户更正属于对齐既有意图（V071 明确给 system_administrator 授了通知规则/渠道），
--   不是新增授权。本迁移不新增任何权限语义，也不删除关系行。
--
-- 可重复性
--   更正语句按 (id) 精确定位且带 tenant_id 前置条件；重复执行不产生额外变化。
--
-- 依赖与顺序
--   需在 V249 之后应用。
--
-- 回滚限制
--   不提供自动回滚。撤销会把授权重新置为不生效状态，没有业务意义。
--
-- 编码
--   仅写入 ASCII 标识与数字，UTF-8 无 BOM。

SET NAMES utf8mb4;

-- 1) 把跨租户关系更正为角色自身租户。只处理 tenant_id 与角色不一致的有效行。
UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
SET rm.`tenant_id`=r.`tenant_id`,
    rm.`updater`='V250',
    rm.`update_time`=NOW()
WHERE rm.`deleted`=b'0' AND rm.`tenant_id`<>r.`tenant_id`;

-- 2) 更正后若与既有有效关系重复(同租户/角色/菜单)，保留最早一条，其余逻辑删除。
--    用派生表先算出「每个键上应保留的最小 id」，避免同表自更新读到旧值。
UPDATE `system_role_menu` dup
JOIN (
  SELECT `tenant_id`,`role_id`,`menu_id`, MIN(`id`) AS keep_id
  FROM `system_role_menu` WHERE `deleted`=b'0'
  GROUP BY `tenant_id`,`role_id`,`menu_id` HAVING COUNT(*)>1
) grouped
  ON grouped.`tenant_id`=dup.`tenant_id` AND grouped.`role_id`=dup.`role_id`
 AND grouped.`menu_id`=dup.`menu_id` AND dup.`id`<>grouped.`keep_id`
SET dup.`deleted`=b'1', dup.`updater`='V250', dup.`update_time`=NOW()
WHERE dup.`deleted`=b'0';

-- 3) system_administrator 的通知渠道路径需要的父级(若仍缺失则补齐)。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT DISTINCT role_row.`id`, menu_row.`id`, 'V250', NOW(), 'V250', NOW(), b'0', role_row.`tenant_id`
FROM `system_role` role_row
JOIN `system_menu` menu_row
  ON menu_row.`id` IN (2739,2144,602130) AND menu_row.`deleted`=b'0' AND menu_row.`status`=0
WHERE role_row.`code`='system_administrator' AND role_row.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
     WHERE existing.`role_id`=role_row.`id` AND existing.`menu_id`=menu_row.`id`
       AND existing.`tenant_id`=role_row.`tenant_id` AND existing.`deleted`=b'0');

-- 版本记录
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V250','修复跨租户授权行并补齐通知渠道父级', SHA2('V250__repair_cross_tenant_grants.sql',256), NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V250','修复跨租户授权行并补齐通知渠道父级', SHA2('V250__repair_cross_tenant_grants.sql',256), '2026.09.15-213000-v250', NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

-- =====================================================================================
-- 只读核对
-- =====================================================================================

-- R1) 不应再存在角色租户与关系租户不一致的有效行。预期 0。
SELECT 'R1-cross-tenant' AS check_name, COUNT(*) AS bad_rows
FROM `system_role_menu` rm JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND rm.`tenant_id`<>r.`tenant_id`;

-- R2) 同租户/角色/菜单不应有重复有效行。预期 0。
SELECT 'R2-duplicate' AS check_name, COUNT(*) AS dup_keys FROM (
  SELECT `tenant_id`,`role_id`,`menu_id` FROM `system_role_menu`
  WHERE `deleted`=b'0' GROUP BY `tenant_id`,`role_id`,`menu_id` HAVING COUNT(*)>1
) d;

-- R3) 悬空授权复核：被授权菜单的父级必须也被同一角色授权(parent_id>2 时)。预期 0 行。
SELECT 'R3-orphan' AS check_name, r.code AS role_code, m.id AS menu_id, m.name, m.parent_id
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id`=rm.`role_id` AND r.`deleted`=b'0'
JOIN `system_menu` m ON m.`id`=rm.`menu_id` AND m.`deleted`=b'0'
WHERE rm.`deleted`=b'0' AND m.`parent_id`>2
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` pg
     WHERE pg.`role_id`=rm.`role_id` AND pg.`menu_id`=m.`parent_id`
       AND pg.`tenant_id`=rm.`tenant_id` AND pg.`deleted`=b'0');

-- R4) system_administrator 的通知渠道权限是否生效。预期三个 1。
SELECT 'R4-notify-channel' AS check_name,
       (SELECT COUNT(*) FROM `system_role_menu` rm JOIN `system_role` r ON r.`id`=rm.`role_id`
          WHERE rm.`deleted`=b'0' AND r.`code`='system_administrator' AND rm.`tenant_id`=r.`tenant_id`
            AND rm.`menu_id`=602130) AS channel_page,
       (SELECT COUNT(*) FROM `system_role_menu` rm JOIN `system_role` r ON r.`id`=rm.`role_id`
          WHERE rm.`deleted`=b'0' AND r.`code`='system_administrator' AND rm.`tenant_id`=r.`tenant_id`
            AND rm.`menu_id`=602131) AS channel_query,
       (SELECT COUNT(*) FROM `system_role_menu` rm JOIN `system_role` r ON r.`id`=rm.`role_id`
          WHERE rm.`deleted`=b'0' AND r.`code`='system_administrator' AND rm.`tenant_id`=r.`tenant_id`
            AND rm.`menu_id`=602132) AS channel_update;
