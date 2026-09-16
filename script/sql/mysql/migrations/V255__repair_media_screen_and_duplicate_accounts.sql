-- V255: 修复大屏贡献人快照的悬空用户 ID，并清理重名重复账号
--
-- 事故背景
--   大屏（媒体大屏 / public-api/zsjos/media-screen）按客资上冻结的贡献人快照聚合：
--     `zsjos_lead.contribution_user_id_snapshot` JOIN `system_users`
--   （LeadMapper.countMediaScreenContributions，再由 MediaScreenQueryService 与当前花名册对齐）。
--
--   合并遗留数据时，这批快照列写的是**备份库原来的员工 ID**；随后
--   `script/sql/merge/cleanup_imported_staff.py` 把"零业务引用"的导入账号物理删除，
--   而它的引用扫描只覆盖 `*_user_id` 命名，**没有覆盖带 `_snapshot` 后缀的列**。
--   结果：23 个旧 ID 中 16 个指向已不存在的账号，3,075 行客资因此无人认领，
--   大屏上"很多人数据为空"，只有 ID 恰好还活着的少数几个才有数。
--
--   本迁移只修 `contribution_user_id_snapshot`；`contribution_supervisor_user_id_snapshot`
--   经全量核对无悬空，不在范围内。
--
-- 本迁移做三件事
--   1) 悬空贡献人快照 -> 唯一同名在职账号（3,072 行）
--   2) 重名重复账号合并：导入账号 232(陈薇)/259(莫才巧) 的客资快照归并到已启用的
--      同名账号 20/19，再逻辑删除导入账号；418(梁颖, 已禁用无引用) 一并逻辑删除
--   3) 无对应员工的兼职端身份"陆毅兼职端"(快照 ID 265) 的 3 条客资整体逻辑删除
--
-- 修复口径
--   1) 对悬空行，按贡献人姓名快照在当前启用员工中**唯一匹配**后回填：
--      - 姓名匹配到恰好一个 `deleted=b'0'` 的用户才回填；
--      - 同名多账号不参与自动回填（由本迁移第 2 步显式合并），避免指错人；
--      - 匹配不到或不唯一的保持原样，由文末只读核对列出，交人工判断。
--      ID 能解析的行**一律不动**。
--
--   2) 232/259 是导入补建的账号，在业务库里无角色、无登录记录；20/19 是既有账号，
--      持有 `dept_manager` 且 `system_dept.leader_user_id` 指向它们（部门主管）。
--      合并方向固定为"导入账号 -> 既有账号"：按角色/主管身份判定，不按创建时间。
--      只改归属与快照，不改任何客资的业务状态、金额、跟进记录。
--      账号本身逻辑删除（`deleted=1`）而非物理删除，保留审计可追溯性。
--
--   3) 265 是一个兼职端身份，当前 `system_users` 中没有任何账号与之对应，
--      且这 3 条客资无订单、无反馈，仅为历史无效/关闭线索。整体逻辑删除闭包：
--      客资 + 其跟进记录 + 指派历史 + 意向产品。人员档案(`zsjos_person`)保留。
--
-- 可重复性
--   回填只命中"当前 ID 悬空 且 姓名唯一匹配"的行；合并/删除均带 `deleted=b'0'` 守卫。
--   重复执行无行命中。
--
-- 依赖与顺序
--   需在合并导入（merge_legacy_business_data.py）与员工清理（cleanup_imported_staff.py）之后应用。
--
-- 回滚限制
--   不提供自动回滚。回退会把快照改回悬空 ID、恢复重复账号与 3 条已删客资，
--   即恢复本迁移修复的缺陷。执行前请确认已有可用备份。
--
-- 编码
--   源文件与客户端连接均为 utf8mb4。

SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1) 悬空贡献人快照 -> 唯一同名在职账号
-- ---------------------------------------------------------------------------
UPDATE `zsjos_lead` lead_row
JOIN `system_users` user_row
  ON user_row.`nickname` = lead_row.`contribution_user_name_snapshot`
 AND user_row.`deleted` = b'0'
SET lead_row.`contribution_user_id_snapshot` = user_row.`id`
WHERE lead_row.`deleted` = b'0'
  AND lead_row.`contribution_user_id_snapshot` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_users` existing
                  WHERE existing.`id` = lead_row.`contribution_user_id_snapshot`)
  AND (SELECT COUNT(*) FROM `system_users` candidate
       WHERE candidate.`nickname` = lead_row.`contribution_user_name_snapshot`
         AND candidate.`deleted` = b'0') = 1;

-- ---------------------------------------------------------------------------
-- 2) 重名重复账号合并（导入账号 -> 既有同名账号）
-- ---------------------------------------------------------------------------
-- 2a) 客资贡献人快照改指既有账号
UPDATE `zsjos_lead`
SET `contribution_user_id_snapshot` = 20
WHERE `deleted` = b'0' AND `contribution_user_id_snapshot` = 232
  AND EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id`=20 AND u.`deleted`=b'0' AND u.`status`=0);

UPDATE `zsjos_lead`
SET `contribution_user_id_snapshot` = 19
WHERE `deleted` = b'0' AND `contribution_user_id_snapshot` = 259
  AND EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id`=19 AND u.`deleted`=b'0' AND u.`status`=0);

-- 2a-2) 主管快照同样需要改指：232/259 作为 `system_dept.leader_user_id` 被冻结进
--       `contribution_supervisor_user_id_snapshot`（大屏部门副标题用它）。删掉重复账号
--       而不改这两列，会让主管快照变成悬空，部门副标题随即显示为空。
UPDATE `zsjos_lead`
SET `contribution_supervisor_user_id_snapshot` = 20
WHERE `deleted` = b'0' AND `contribution_supervisor_user_id_snapshot` = 232
  AND EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id`=20 AND u.`deleted`=b'0' AND u.`status`=0);

UPDATE `zsjos_lead`
SET `contribution_supervisor_user_id_snapshot` = 19
WHERE `deleted` = b'0' AND `contribution_supervisor_user_id_snapshot` = 259
  AND EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id`=19 AND u.`deleted`=b'0' AND u.`status`=0);

-- 部门主管指向：1011 的主管原本可能被指向导入账号 232，改指既有账号 20
UPDATE `system_dept`
SET `leader_user_id` = 20, `updater` = 'V255', `update_time` = NOW()
WHERE `id` = 1011 AND `leader_user_id` = 232
  AND EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id`=20 AND u.`deleted`=b'0' AND u.`status`=0);

UPDATE `system_dept`
SET `leader_user_id` = 19, `updater` = 'V255', `update_time` = NOW()
WHERE `id` = 1012 AND `leader_user_id` = 259
  AND EXISTS (SELECT 1 FROM `system_users` u WHERE u.`id`=19 AND u.`deleted`=b'0' AND u.`status`=0);

-- 2b) 逻辑删除导入账号及其角色/岗位关系（418 已禁用无引用，一并处理）
UPDATE `system_users`
SET `deleted` = b'1', `status` = 1, `updater` = 'V255', `update_time` = NOW()
WHERE `id` IN (232, 259, 418) AND `deleted` = b'0';

UPDATE `system_user_role`
SET `deleted` = b'1', `updater` = 'V255', `update_time` = NOW()
WHERE `user_id` IN (232, 259, 418) AND `deleted` = b'0';

UPDATE `system_user_post`
SET `deleted` = b'1', `updater` = 'V255', `update_time` = NOW()
WHERE `user_id` IN (232, 259, 418) AND `deleted` = b'0';

-- ---------------------------------------------------------------------------
-- 3) 无对应员工的兼职端身份：陆毅兼职端（快照 ID 265）的 3 条客资整体逻辑删除
-- ---------------------------------------------------------------------------
UPDATE `zsjos_lead_follow_up_record`
SET `deleted` = b'1', `updater` = 'V255', `update_time` = NOW()
WHERE `lead_id` IN (80, 214, 297) AND `deleted` = b'0';

UPDATE `zsjos_lead_assignment_history`
SET `deleted` = b'1', `updater` = 'V255', `update_time` = NOW()
WHERE `lead_id` IN (80, 214, 297) AND `deleted` = b'0';

UPDATE `zsjos_lead_intended_product`
SET `deleted` = b'1', `updater` = 'V255', `update_time` = NOW()
WHERE `lead_id` IN (80, 214, 297) AND `deleted` = b'0';

UPDATE `zsjos_lead`
SET `deleted` = b'1', `updater` = 'V255', `update_time` = NOW()
WHERE `id` IN (80, 214, 297) AND `deleted` = b'0';

-- ---------------------------------------------------------------------------
-- 只读核对
-- ---------------------------------------------------------------------------
-- 应为 0 行；若仍有行，说明存在本迁移无法自动判定的悬空贡献人
SELECT 'V255-remaining-orphan-contributor' AS check_name,
       lead_row.`contribution_user_id_snapshot` AS snapshot_id,
       lead_row.`contribution_user_name_snapshot` AS snapshot_name,
       COUNT(*) AS lead_count
FROM `zsjos_lead` lead_row
WHERE lead_row.`deleted` = b'0'
  AND lead_row.`contribution_user_id_snapshot` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `system_users` existing
                  WHERE existing.`id` = lead_row.`contribution_user_id_snapshot`)
GROUP BY lead_row.`contribution_user_id_snapshot`, lead_row.`contribution_user_name_snapshot`;

-- 应为 0 行；若仍有行，说明还有同名多账号未收敛
SELECT 'V255-remaining-duplicate-nickname' AS check_name, `nickname`, COUNT(*) AS account_count
FROM `system_users`
WHERE `deleted` = b'0' AND `nickname` IN ('陈薇','莫才巧','梁颖')
GROUP BY `nickname` HAVING COUNT(*) > 1;

COMMIT;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V255','修复大屏贡献人快照的悬空用户 ID 并清理重名重复账号',
        SHA2('V255__repair_media_screen_and_duplicate_accounts.sql',256),NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V255','修复大屏贡献人快照的悬空用户 ID 并清理重名重复账号',
        SHA2('V255__repair_media_screen_and_duplicate_accounts.sql',256),'2026.09.16-190000-v255',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
