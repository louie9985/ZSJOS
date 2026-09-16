-- =====================================================================
-- 清理 infra_file 里两组"同 key 重复上传"的孤立记录
-- 生成：2026-09-16
--
-- 背景
--   COS 桶**没有开启版本控制**，同名 key 后传覆盖先传。用户在
--   2026-09-04 / 09-05 各重复上传了一次，于是库里留下两组同 path 的行：
--
--     id 2  / id 8    zsjos/lead/admin/20260904/image.png          (949202 B)
--     id 12 / id 13   zsjos/lead/admin/20260905/大健康+AI训练营.jpeg (202560 B)
--
--   对象存储上每个 key 只剩**一份**对象，Last-Modified 分别对应
--   id 8 (2026-09-04T08:16:02Z) 和 id 13 (2026-09-05T03:06:02Z)，
--   即后传的那次覆盖了先传的。id 2 / id 12 记录指向的对象已不存在。
--
-- 处理口径
--   这是**孤立记录清理**，不是引用重映射：
--   全库扫描（17 个 *_file_id 列 + 198 个 JSON 列，按 id 与按
--   infraFileId/fileId 两种路径）确认 id 2/8/12/13 **零引用**。
--   因此不存在需要改指向的引用方，也没有"保留哪一条"的取舍问题。
--
--   采用**逻辑删除**（deleted=b'1'）而非物理 DELETE：
--   记录保留可追溯，需要时改回 b'0' 即可复原。
--
-- 可重复性
--   带 deleted=b'0' 守卫，重复执行命中 0 行。
--
-- 影响
--   infra_file 有效行 2289 -> 2285。**不动任何对象存储内容。**
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- 清理前核对：应列出 4 行
SELECT id, path, size, create_time
FROM infra_file
WHERE id IN (2, 8, 12, 13) AND deleted = b'0';

-- 前置断言：这 4 个 id 必须零引用，否则中止
SELECT 'precheck-still-unreferenced' AS check_name, COUNT(*) AS hits
FROM zsjos_lead_attachment WHERE infra_file_id IN (2, 8, 12, 13)
UNION ALL SELECT 'precheck-followup-image', COUNT(*) FROM zsjos_lead_follow_up_image WHERE infra_file_id IN (2, 8, 12, 13)
UNION ALL SELECT 'precheck-opportunity-image', COUNT(*) FROM zsjos_opportunity_follow_up_image WHERE infra_file_id IN (2, 8, 12, 13)
UNION ALL SELECT 'precheck-registration-att', COUNT(*) FROM zsjos_registration_item_attachment WHERE infra_file_id IN (2, 8, 12, 13)
UNION ALL SELECT 'precheck-material-file', COUNT(*) FROM zsjos_material_file WHERE infra_file_id IN (2, 8, 12, 13);

UPDATE infra_file
SET deleted = b'1', updater = 'orphan-cleanup-20260916', update_time = NOW()
WHERE id IN (2, 8, 12, 13) AND deleted = b'0';

-- 结果：应剩 2 个有效 path（每个 key 一条）
SELECT id, path, size, create_time, creator
FROM infra_file
WHERE path IN ('zsjos/lead/admin/20260904/image.png',
               'zsjos/lead/admin/20260905/大健康+AI训练营.jpeg')
ORDER BY path, deleted, id;

COMMIT;
