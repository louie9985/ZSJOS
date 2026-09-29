-- ZSJ-OS 历史客资接收与业绩归属数据补齐
--
-- 背景：旧库 (parttimecrm) 的 cst+0800 导出为东八区本地时间。2026-09-02 之前导入的数据
-- 被按 UTC 解析，提交类时间整体早 8 小时；而按事件逐条转换的接收类时间是正确的。
-- 结果是同一条客资的接收时间晚于自己的提交时间，60 日有效期判定失效。
-- 本脚本先修正时间轴，再补缺失的接收批次与接收归属快照。
--
-- 边界：只处理 2026-09-02 之前的数据。该日之后导入的数据编码正确，一律不触碰。
--       校验结果：截止点前不存在未偏移的行，截止点后不存在已偏移的行（0 / 0）。
--
-- 幂等：本脚本必须可安全重复执行。两个机制：
--   * 时间修正（步骤 1、2）是「一次性列级修正」，用 zsjos_data_repair_marker 打标记。
--     注意：不能用「值 < 截止点」当幂等条件——加 8 小时后值仍小于截止点，会二次偏移。
--   * 接收批次与快照用 NOT EXISTS 去重，天然幂等。
-- 副作用：只写业务事实与快照，不触发派单、通知、任务创建或审批。
--
-- 重要：本脚本不修改订单金额、订单状态、审批结果或其他业务语义。

SET NAMES utf8mb4;

-- 一次性数据修正的完成标记。列级粒度的「已修复」记录，使修正步骤可重复执行而不会二次偏移。
CREATE TABLE IF NOT EXISTS zsjos_data_repair_marker (
  id          bigint       NOT NULL AUTO_INCREMENT,
  repair_key  varchar(100) NOT NULL COMMENT '修正批次标识',
  table_name  varchar(64)  NOT NULL COMMENT '目标表',
  column_name varchar(64)  NOT NULL COMMENT '目标列',
  row_count   int          NULL COMMENT '本次修正影响行数',
  applied_at  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_repair_marker (repair_key, table_name, column_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ZSJOS 一次性数据修正执行标记';

-- ===========================================================================
-- 步骤 1：修正提交类时间（+8 小时，改回东八区）
-- ===========================================================================
-- 每列先判标记，再修正，最后写标记。标记已存在时整条 UPDATE 不命中任何行。

-- 连接排序规则可能是 utf8mb4_0900_ai_ci，而表为 utf8mb4_unicode_ci；
-- 显式指定排序规则，避免比较时 collation 混用报错。
SET @rk := 'legacy-utc-shift-20260902' COLLATE utf8mb4_unicode_ci;

UPDATE zsjos_lead SET submitted_at = DATE_ADD(submitted_at, INTERVAL 8 HOUR)
 WHERE submitted_at IS NOT NULL AND submitted_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_lead' AND m.column_name='submitted_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_lead','submitted_at',@rc);

UPDATE zsjos_lead SET counted_at = DATE_ADD(counted_at, INTERVAL 8 HOUR)
 WHERE counted_at IS NOT NULL AND counted_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_lead' AND m.column_name='counted_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_lead','counted_at',@rc);

UPDATE zsjos_lead SET ownership_started_at = DATE_ADD(ownership_started_at, INTERVAL 8 HOUR)
 WHERE ownership_started_at IS NOT NULL AND ownership_started_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_lead' AND m.column_name='ownership_started_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_lead','ownership_started_at',@rc);

UPDATE zsjos_lead SET last_follow_up_at = DATE_ADD(last_follow_up_at, INTERVAL 8 HOUR)
 WHERE last_follow_up_at IS NOT NULL AND last_follow_up_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_lead' AND m.column_name='last_follow_up_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_lead','last_follow_up_at',@rc);

UPDATE zsjos_order SET submitted_at = DATE_ADD(submitted_at, INTERVAL 8 HOUR)
 WHERE submitted_at IS NOT NULL AND submitted_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_order' AND m.column_name='submitted_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_order','submitted_at',@rc);

UPDATE zsjos_order SET customer_paid_at = DATE_ADD(customer_paid_at, INTERVAL 8 HOUR)
 WHERE customer_paid_at IS NOT NULL AND customer_paid_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_order' AND m.column_name='customer_paid_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_order','customer_paid_at',@rc);

UPDATE zsjos_lead_follow_up_record SET occurred_at = DATE_ADD(occurred_at, INTERVAL 8 HOUR)
 WHERE occurred_at IS NOT NULL AND occurred_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_lead_follow_up_record' AND m.column_name='occurred_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_lead_follow_up_record','occurred_at',@rc);

UPDATE zsjos_lead_follow_up_record SET next_follow_up_at = DATE_ADD(next_follow_up_at, INTERVAL 8 HOUR)
 WHERE next_follow_up_at IS NOT NULL AND next_follow_up_at < '2026-09-02 00:00:00'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_lead_follow_up_record' AND m.column_name='next_follow_up_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_lead_follow_up_record','next_follow_up_at',@rc);

-- ===========================================================================
-- 步骤 2：修正分配历史中「随客资提交时间派生」的行
-- ===========================================================================
-- 关键区分（已逐类核对）：
--   * action_type='other' -> occurred_at 复制自客资 submitted_at，同样偏移，需要 +8h
--   * accept/claim/transfer/public_pool -> 复制自旧库真实事件时间，本来正确，不得偏移

UPDATE zsjos_lead_assignment_history SET occurred_at = DATE_ADD(occurred_at, INTERVAL 8 HOUR)
 WHERE action_type = 'other' AND occurred_at < '2026-09-02 00:00:00' AND creator LIKE 'legacy%'
   AND NOT EXISTS (SELECT 1 FROM zsjos_data_repair_marker m WHERE m.repair_key=@rk AND m.table_name='zsjos_lead_assignment_history' AND m.column_name='occurred_at');
SET @rc := ROW_COUNT();
INSERT IGNORE INTO zsjos_data_repair_marker (repair_key, table_name, column_name, row_count) VALUES (@rk,'zsjos_lead_assignment_history','occurred_at',@rc);

-- ===========================================================================
-- 步骤 3：补齐缺失的接收批次（全部批次）
-- ===========================================================================
-- 来源：旧库 dashboard_lead_sales_receipt_event（每行一个真实接收事件）。
-- 关联：lead_no 关联客资；sales_id 经「手机号 -> username」映射到当前用户。
-- 动作映射（按 event_type 精确对应，不按数量归类）：
--   assign_accept -> accept | sales_manual_entry -> accept
--   pool_claim -> claim | supervisor_transfer -> transfer
--   valid_transfer -> 不算新接收（is_new_lead_receipt=0），不生成接收批次
-- 注意：旧库时间为微秒精度，写入 datetime 会被截断到秒，去重比较必须同样截断，
--       否则 CAST 前的时间永远不相等，重复执行会不断重复插入。
-- 幂等：同一 (lead_id, action_type, occurred_at, to_owner_user_id) 已存在则跳过。

INSERT INTO zsjos_lead_assignment_history
  (lead_id, action_type, from_owner_user_id, to_owner_user_id, operator_user_id,
   reason, occurred_at, creator, create_time, updater, update_time, deleted,
   tenant_id, assignment_rule_id, attempt_no, candidate_user_id, expires_at,
   response_at, owner_identity_snapshot)
SELECT l.id,
       CASE e.event_type
         WHEN 'assign_accept' THEN 'accept'
         WHEN 'sales_manual_entry' THEN 'accept'
         WHEN 'pool_claim' THEN 'claim'
         WHEN 'supervisor_transfer' THEN 'transfer'
       END,
       NULL, m.new_id, COALESCE(m.new_id, 1),
       CONCAT('旧库接收事件回填:', e.event_type),
       CAST(e.occurred_at AS DATETIME),
       'legacy-receipt-backfill-20260928', '2026-09-28 16:00:00',
       'legacy-receipt-backfill-20260928', '2026-09-28 16:00:00',
       b'0', l.tenant_id, NULL, NULL, NULL, NULL, NULL, NULL
  FROM legacy_pcrm_tmp.dashboard_lead_sales_receipt_event e
  JOIN legacy_pcrm_tmp.leads_lead ll ON ll.id = e.lead_id
  JOIN zsjos_lead l ON l.lead_no = ll.lead_no AND l.deleted = b'0'
  -- 身份映射：旧库员工 -> 账号手机号 -> 当前用户。手机号是唯一可靠匹配键；
  -- username 同为手机号，作为第二优先级兜底。
  JOIN (SELECT p.id AS legacy_id,
               COALESCE(
                 MAX(CASE WHEN nu.mobile  = u.phone  AND COALESCE(u.phone,'') <> '' THEN nu.id END),
                 MAX(CASE WHEN nu.username = u.phone THEN nu.id END)
               ) AS new_id
          FROM legacy_pcrm_tmp.employees_employee_profile p
          LEFT JOIN legacy_pcrm_tmp.accounts_user u ON u.id = p.actor_id
          LEFT JOIN system_users nu ON nu.deleted = 0
         GROUP BY p.id) m ON m.legacy_id = e.sales_id
 WHERE e.is_new_lead_receipt = 1
   AND m.new_id IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM zsjos_lead_assignment_history h
                    WHERE h.lead_id = l.id
                      AND h.action_type = CASE e.event_type
                            WHEN 'assign_accept' THEN 'accept'
                            WHEN 'sales_manual_entry' THEN 'accept'
                            WHEN 'pool_claim' THEN 'claim'
                            WHEN 'supervisor_transfer' THEN 'transfer' END
                      AND h.occurred_at = CAST(e.occurred_at AS DATETIME)
                      AND h.to_owner_user_id = m.new_id);

-- ===========================================================================
-- 步骤 4：补齐接收归属快照（fact_type = ASSIGNMENT）
-- ===========================================================================
-- 口径（用户确认）：部门/中心按「该接收人当前归属」计算，与
-- PerformanceSnapshotService.base() 的现有实现保持一致，不虚构历史组织。
--   dept_id   <- system_users.dept_id
--   center_id <- zsjos_performance_org.center_id（按 dept_id 映射）
-- 统计读取方 PerformanceFactMapper.receipts 通过 fact_type='ASSIGNMENT' AND fact_id=h.id
-- 取 dept_id/center_id，因此必须按接收历史 id 建快照。
-- 只补缺失的接收批次快照，不覆盖任何已有快照。

INSERT INTO zsjos_performance_attribution
  (outcome, completed_at, fact_type, fact_id, user_id, user_name,
   dept_id, dept_name, center_id, center_name, lead_id, assignment_id,
   received_at, org_source, source_group, channel_code, channel_label,
   creator, create_time, updater, update_time, deleted, tenant_id)
SELECT NULL, NULL, 'ASSIGNMENT', h.id, h.to_owner_user_id, u.nickname,
       u.dept_id, d.name, m.center_id, cd.name,
       h.lead_id, h.id, h.occurred_at,
       -- 本批快照的部门/中心取补写时点的当前归属，不是接收当时的历史组织，显式标注。
       'current',
       CASE COALESCE(l.source_type,'')
         WHEN 'internal_new_media' THEN 'inbound'
         WHEN 'partner' THEN 'inbound'
         WHEN 'sales_self_sourced' THEN 'self'
         ELSE 'unknown' END,
       l.source_channel_id, l.source_channel_label_snapshot,
       'legacy-receipt-backfill-20260928', '2026-09-28 16:00:00',
       'legacy-receipt-backfill-20260928', '2026-09-28 16:00:00',
       b'0', h.tenant_id
  FROM zsjos_lead_assignment_history h
  JOIN zsjos_lead l ON l.id = h.lead_id AND l.deleted = b'0'
  JOIN system_users u ON u.id = h.to_owner_user_id AND u.deleted = 0
  LEFT JOIN system_dept d ON d.id = u.dept_id
  LEFT JOIN zsjos_performance_org m ON m.dept_id = u.dept_id AND m.deleted = b'0'
  LEFT JOIN system_dept cd ON cd.id = m.center_id
 WHERE h.action_type IN ('accept','claim','transfer')
   AND h.to_owner_user_id IS NOT NULL
   AND h.deleted = b'0'
   AND NOT EXISTS (SELECT 1 FROM zsjos_performance_attribution a
                    WHERE a.fact_type = 'ASSIGNMENT' AND a.fact_id = h.id);
