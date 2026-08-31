-- ZSJOS 时间契约只读核验脚本。
--
-- 依赖与顺序：连接目标业务库后直接执行；要求 zsjos_lead 已存在。
-- 数据范围：只读取当前库的时区配置、ZSJOS 时间列定义和客资时间异常数量/编号。
-- 可重复性：脚本不创建对象、不写入或删除数据，可重复执行。
-- 回滚与恢复：无状态变更，因此不需要回滚。

SELECT @@global.time_zone AS global_time_zone,
       @@session.time_zone AS session_time_zone,
       NOW() AS session_now,
       UTC_TIMESTAMP() AS utc_now;

SELECT table_name,
       column_name,
       column_type,
       column_default,
       extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name LIKE 'zsjos\_%' ESCAPE '\\'
  AND (column_name LIKE '%\_at' ESCAPE '\\'
       OR column_name IN ('create_time', 'update_time'))
ORDER BY table_name, ordinal_position;

SELECT COUNT(*) AS lead_time_anomaly_count
FROM zsjos_lead
WHERE update_time < submitted_at
   OR update_time < create_time;

-- 仅输出业务编号和时间，不输出姓名、手机号等个人信息。
SELECT id,
       submitted_at,
       create_time,
       update_time,
       TIMESTAMPDIFF(SECOND, submitted_at, update_time) AS submitted_to_update_seconds
FROM zsjos_lead
WHERE update_time < submitted_at
   OR update_time < create_time
ORDER BY id;
