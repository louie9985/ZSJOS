-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V247: 退役兼职端 System 角色 part_time_partner，并清除其残留菜单授权
--
-- 背景
--   兼职自 V072「Independent Partner identity」起已具备完全独立的身份与鉴权链路：
--     账号表   zsjos_partner_account
--     认证     /admin-api/zsjos/auth/login (PartnerAppAuthController)
--     令牌主体 zsjos_partner_account.id，UserTypeEnum.PARTNER(2)
--     权限来源 PartnerAuthServiceImpl.PORTAL_PERMISSIONS（编译期常量集合）
--   兼职端从读取 system_role_menu，admin/workbench 与兼职端互为独立用户体系，
--   双向不得交叉登录。V072 已删除兼职的系统用户绑定、系统角色关系与 user_type=1 令牌。
--
--   但 V063 引入的同名 System 角色 part_time_partner 从未被清理，长期作为"影子角色"
--   参与 system_role_menu 配置。它的存在会误导管理员以为可以通过它配置兼职端权限，
--   而实际上兼职端根本不消费该角色的授权。本迁移将其退役。
--
-- 处理范围（全部限定 tenant 内 code='part_time_partner' 或本文件列明的稳定菜单）
--   1. 删除该角色的全部 system_role_menu 关系（含 V063/V068/sync-existing-server 与 V246 残留）。
--   2. 删除该角色的全部 system_user_role 关系（V072 已删除存量，此处为幂等兜底）。
--   3. 在确认该角色没有任何有效 system_user_role 绑定后，逻辑删除该 system_role 行。
--   4. 退役孤儿菜单 6909「返现查询」，它是 V063 兼职端菜单在 V069/V070 删除父节点后
--      被挂到「工作计划」(6900) 下的遗留项，唯一持有者是 part_time_partner 与 super_admin。
--
-- 可重复性
--   所有语句均为幂等 UPDATE/DELETE；重复执行不产生额外影响。第 3 步带存在性保护，
--   一旦角色仍被系统用户绑定则跳过，不会造成悬空引用。
--
-- 依赖与顺序
--   需在 V246 之后应用。V246 已不含 part_time_partner 的目标关系。
--
-- 回滚限制
--   本迁移不提供自动回滚。恢复该角色需重新插入 system_role 行并重建所需关系，
--   但兼职端不消费这些授权，因此恢复没有业务意义。
--
-- 编码
--   仅写入 ASCII 角色编码、稳定菜单 ID 与中文说明，UTF-8 无 BOM。

SET NAMES utf8mb4;

-- 2) 清除该角色的系统用户绑定（V072 之后应为空，此处幂等兜底）。
DELETE ur FROM `system_user_role` ur
JOIN `system_role` r ON r.`id`=ur.`role_id`
WHERE r.`code`='part_time_partner';

-- 3) 逻辑删除该 System 角色。仅当仍无有效系统用户绑定该角色时执行。
UPDATE `system_role` r
SET r.`deleted`=b'1',
    r.`status`=1,
    r.`remark`='已退役: 兼职端使用独立账号体系 zsjos_partner_account，不通过 System 角色授权',
    r.`updater`='V247',
    r.`update_time`=NOW()
WHERE r.`code`='part_time_partner' AND r.`deleted`=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM (
      SELECT ur.`role_id` FROM `system_user_role` ur
      WHERE ur.`deleted`=b'0'
    ) bound WHERE bound.`role_id`=r.`id`);

UPDATE `system_menu` m
SET m.`deleted`=b'1', m.`status`=1, m.`updater`='V247', m.`update_time`=NOW()
WHERE m.`id`=6909 AND m.`deleted`=b'0';

-- 版本记录
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V247','退役兼职端 System 角色与残留授权', SHA2('V247__retire_partner_system_role.sql',256), NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V247','退役兼职端 System 角色与残留授权', SHA2('V247__retire_partner_system_role.sql',256), '2026.09.15-190500-v247', NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

-- =====================================================================================
-- 只读核对
-- =====================================================================================

-- R1) 该角色是否仍存在有效行、是否仍持有菜单或用户绑定。预期三列同时为 0。
SELECT 'R1-partner-role' AS check_name,
       (SELECT COUNT(*) FROM `system_role` WHERE `code`='part_time_partner' AND `deleted`=b'0') AS active_role,
       (SELECT COUNT(*) FROM `system_role_menu` rm JOIN `system_role` r ON r.`id`=rm.`role_id`
          WHERE r.`code`='part_time_partner' AND rm.`deleted`=b'0') AS active_menu_grants,
       (SELECT COUNT(*) FROM `system_user_role` ur JOIN `system_role` r ON r.`id`=ur.`role_id`
          WHERE r.`code`='part_time_partner' AND ur.`deleted`=b'0') AS bound_users;

-- R2) 孤儿菜单 6909 是否已退役。预期 retired=1, active_grants=0。
SELECT 'R2-orphan-menu' AS check_name,
       (SELECT COUNT(*) FROM `system_menu` WHERE `id`=6909 AND `deleted`=b'1') AS retired,
       (SELECT COUNT(*) FROM `system_role_menu` WHERE `menu_id`=6909 AND `deleted`=b'0') AS active_grants;

-- R3) 兼职独立身份链路仍然完好（不应受本迁移影响）。
SELECT 'R3-partner-identity' AS check_name,
       (SELECT COUNT(*) FROM `zsjos_partner` WHERE `deleted`=b'0') AS partners,
       (SELECT COUNT(*) FROM `zsjos_partner_account` WHERE `deleted`=b'0') AS partner_accounts;

-- R4) 确认没有其他角色因本迁移被牵连。预期返回 0 行。
SELECT 'R4-collateral' AS check_name, r.code, COUNT(*) AS grants
FROM `system_role_menu` rm JOIN `system_role` r ON r.`id`=rm.`role_id`
WHERE rm.`deleted`=b'0' AND rm.`updater`='V247'
GROUP BY r.code;
