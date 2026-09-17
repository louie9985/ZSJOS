-- UTF-8. 2026-09-17: role-menu assignments are administrator-owned.
-- Automatic grants, inheritance, revocation and reconciliation have been retired.
-- Scope/prerequisites/order: unchanged except role-menu writes; existing grants are preserved.
-- Source cleanup only: deployed checksums require a reviewed rollout; do not auto-reconcile.
-- Replay never assigns roles; rollback does not restore historical automatic grants.
-- Historical rationale below predates the policy above; grant operations described there are retired.
-- V254: 补齐三处真实数据缺口（一级品类返现默认值 / V128 版本登记 / 考期日历查询叶）
--
-- 缺口一：一级商品品类缺少返现默认值
--   V063 与 V122 都用 `COALESCE(...,10.00)` / `COALESCE(...,0.1000)` 给
--   `parent_id=0` 的品类补默认有效返现金额与成交返现比例。但一级品类（会员服务、
--   医药与中医、技能提升、职业考证-* 共 6 行）是 V219「2026 商品目录」才插入的，
--   排在 V063/V122 之后。因此新建库里这 6 行两个默认值始终为 NULL，
--   `verify-bootstrap.sql` 的 `V063 cashback defaults` 断言必然失败——这是真实缺口，
--   不是断言陈旧：管理员在该品类下建商品时拿不到默认返现口径。
--
--   本迁移按 V063/V122 的原口径回填，只补 NULL，不覆盖管理员已配置的值。
--
-- 缺口二：V128 没有登记版本
--   `V128__media_director_student_flow.sql` 加齐了 11 个 director_* 列、建了
--   positioning-card 运营确认/退回权限并做了授权，但全文没有任何
--   `zsjos_schema_version` / `zsjos_module_schema_version` 写入，是漏写。
--   同批的 V131 登记了 legacy 表却漏了 module 表。两者都导致校验断言无法通过，
--   也让"已应用迁移"的账本缺少这两条记录。
--
--   本迁移只为这两个版本补登记（V128 进两张表，V131 补 module 表）。
--   不重放它们的业务写入——那些写入早已生效。
--
-- 缺口三：考期日历 73610 的查询叶 73612 缺失
--   V187 建立契约：授权了页面 73610 的角色必须同时持有查询叶 73612（否则"页面可见、
--   查询按钮不可达"，且 73612 是角色树里表达只读访问的唯一可选叶）。
--   V251「通用菜单收敛」在撤回业务角色通用菜单时，把 73610 留给了需要它的业务角色
--   （dept_manager / enrollment_manager / exam_manager / exam_specialist），但没有同步
--   回补 73612——这些角色因此退化为"能进页面但查不了"。
--   `verify-bootstrap.sql` 的 `V187 exam calendar` 断言抓到的正是这个缺陷。
--
--   本迁移按 V187 的原口径回补：任何持有 73610 的角色，补上 73612。
--
-- 可重复性
--   回填只命中 NULL；登记与授权均为 not-exists 保护插入。重复执行不产生新变化。
--
-- 依赖与顺序
--   需在 V187、V219、V128、V131、V251 之后应用。
--
-- 回滚限制
--   不提供自动回滚。回退需删除本迁移补的登记行与 `creator='V254'` 的授权行，
--   并把默认值改回 NULL，但那会恢复上述缺陷。
--
-- 编码
--   源文件与客户端连接均为 utf8mb4。

SET NAMES utf8mb4;

START TRANSACTION;

-- 缺口一：一级品类返现默认值（口径同 V063/V122，只补 NULL）
UPDATE `zsjos_product_category`
SET `default_valid_cashback_amount`=COALESCE(`default_valid_cashback_amount`,10.00),
    `default_deal_cashback_rate`=COALESCE(`default_deal_cashback_rate`,0.1000)
WHERE `deleted`=b'0'
  AND `parent_id`=0
  AND (`default_valid_cashback_amount` IS NULL OR `default_deal_cashback_rate` IS NULL);

-- 缺口二：补 V128 / V131 的版本登记（只补缺失行）
INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
SELECT 'V128','Director student workflow foundation',
       SHA2('V128__media_director_student_flow.sql',256),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V128');

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V128','Director student workflow foundation',
       SHA2('V128__media_director_student_flow.sql',256),'backfill',NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                  WHERE `module_code`='core' AND `version`='V128');

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core','V131','Repair director/operator actions and relationship scene',
       SHA2('V131__repair_director_operator_action_permissions.sql',256),'backfill',NOW()
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                  WHERE `module_code`='core' AND `version`='V131');

-- 只读核对：均应为 0 行
SELECT 'V254-remaining-null-category-default' AS check_name, `id`, `name`
FROM `zsjos_product_category`
WHERE `deleted`=b'0' AND `parent_id`=0
  AND (`default_valid_cashback_amount` IS NULL OR `default_deal_cashback_rate` IS NULL);

SELECT 'V254-remaining-exam-calendar-page-without-query' AS check_name, `role_id`, `tenant_id`
FROM `system_role_menu` source
WHERE source.`menu_id`=73610 AND source.`deleted`=b'0'
  AND NOT EXISTS (SELECT 1 FROM `system_role_menu` query_grant
                  WHERE query_grant.`role_id`=source.`role_id` AND query_grant.`tenant_id`=source.`tenant_id`
                    AND query_grant.`menu_id`=73612 AND query_grant.`deleted`=b'0');

COMMIT;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V254','补齐一级品类返现默认值、V128/V131 版本登记与考期日历查询叶',
        SHA2('V254__backfill_category_cashback_defaults_and_ledger.sql',256),NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V254','补齐一级品类返现默认值、V128/V131 版本登记与考期日历查询叶',
        SHA2('V254__backfill_category_cashback_defaults_and_ledger.sql',256),'2026.09.16-180000-v254',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
