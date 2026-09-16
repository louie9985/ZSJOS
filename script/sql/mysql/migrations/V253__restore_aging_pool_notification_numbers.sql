-- V253: 恢复公海通知模板的客资编号变量（V159 措辞迁移误用 {{lead.name}}）
--
-- 事故背景
--   V085 已确立业务通知不得携带客户姓名的契约：它把客资/订单/报名通知模板里的
--   `{{lead.name}}` / `{{order.studentName}}` / `{{student.name}}` 统一替换为对应业务编号
--   （`{{lead.no}}` / `{{order.no}}`），并同步改写了已渲染的消息快照参数。
--
--   V159「公海术语统一」本意只改用户可见文案（超期公海 → 公海），但它在重写 6 个
--   公海模板的 title/summary/content 时，逐字照抄了 V159 之前的旧文案，把 V085 已换成
--   `{{lead.no}}` 的位置又写回了 `{{lead.name}}`。V177 随后把这 6 个模板克隆成企微副本，
--   于是 12 行（6 场景 × in_app/wecom）全部回退。
--
--   该变量不只是"多显示姓名"的问题：`LeadNotifySceneProvider` 的公海场景只声明并填充
--   `lead.no` 与 `agingPool.*`，payload 里根本没有 `lead.name`。因此这 12 个模板渲染时会
--   留下未替换的占位符原文，通知正文是坏的。
--
-- 本迁移
--   只为 creator='migration-V034' 的 6 个模板及其 creator='migration-V177' 的企微克隆，
--   在 title/summary/content 三列把 `{{lead.name}}` 还原为 `{{lead.no}}`；params 列在
--   V085 已正确写成 ["lead.no",...]，本迁移不改。
--
-- 明确排除
--   仅命中 updater='V159'（in_app）或 updater='migration-V177'（wecom）的行：管理员在
--   后台改过的模板（updater 为其它值或为空）不被覆盖，避免用迁移冲掉人工维护的文案。
--
-- 可重复性
--   替换是幂等的；重复执行不产生新变化（第二次执行无行命中）。
--
-- 依赖与顺序
--   需在 V085、V159、V177 之后应用。
--
-- 回滚限制
--   不提供自动回滚。回退需把 `{{lead.no}}` 改回 `{{lead.name}}`，但那会恢复本迁移修复的缺陷。
--
-- 编码
--   源文件与客户端连接均为 utf8mb4；写入内容含中文文案。

SET NAMES utf8mb4;

START TRANSACTION;

-- in_app 模板：V034 建立、V159 覆盖
UPDATE `system_notify_template`
SET `title`=REPLACE(`title`,'{{lead.name}}','{{lead.no}}'),
    `summary`=REPLACE(`summary`,'{{lead.name}}','{{lead.no}}'),
    `content`=REPLACE(`content`,'{{lead.name}}','{{lead.no}}'),
    `updater`='V253',`update_time`=NOW()
WHERE `deleted`=b'0'
  AND `scene_code` IN ('zsjos.lead.aging_pool_reminder','zsjos.lead.aging_pool_due',
                       'zsjos.lead.aging_pool_assigned','zsjos.lead.aging_pool_reassigned',
                       'zsjos.lead.aging_pool_reassign_required','zsjos.lead.aging_pool_exited')
  AND `creator`='migration-V034'
  AND `updater`='V159'
  AND (`title` LIKE '%{{lead.name}}%' OR `summary` LIKE '%{{lead.name}}%' OR `content` LIKE '%{{lead.name}}%');

-- 企微克隆：V177 从上面的 in_app 模板复制而来
UPDATE `system_notify_template`
SET `title`=REPLACE(`title`,'{{lead.name}}','{{lead.no}}'),
    `summary`=REPLACE(`summary`,'{{lead.name}}','{{lead.no}}'),
    `content`=REPLACE(`content`,'{{lead.name}}','{{lead.no}}'),
    `updater`='V253',`update_time`=NOW()
WHERE `deleted`=b'0'
  AND `scene_code` IN ('zsjos.lead.aging_pool_reminder','zsjos.lead.aging_pool_due',
                       'zsjos.lead.aging_pool_assigned','zsjos.lead.aging_pool_reassigned',
                       'zsjos.lead.aging_pool_reassign_required','zsjos.lead.aging_pool_exited')
  AND `creator`='migration-V177'
  AND `updater`='migration-V177'
  AND (`title` LIKE '%{{lead.name}}%' OR `summary` LIKE '%{{lead.name}}%' OR `content` LIKE '%{{lead.name}}%');

-- 只读核对：应为 0 行；有行则说明存在本迁移未覆盖的公海模板
SELECT 'V253-remaining-lead-name-template' AS check_name, `code`, `channel_code`, `updater`
FROM `system_notify_template`
WHERE `deleted`=b'0' AND `scene_code` LIKE 'zsjos.lead.aging_pool%'
  AND (`title` LIKE '%lead.name%' OR `summary` LIKE '%lead.name%' OR `content` LIKE '%lead.name%' OR `params` LIKE '%lead.name%');

COMMIT;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V253','恢复公海通知模板的客资编号变量',
        SHA2('V253__restore_aging_pool_notification_numbers.sql',256),NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V253','恢复公海通知模板的客资编号变量',
        SHA2('V253__restore_aging_pool_notification_numbers.sql',256),'2026.09.16-180000-v253',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
