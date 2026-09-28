-- ZSJ-OS 重复接收批次清理
--
-- 背景：legacy-increment-20260921 批次重复导入了同一接收事件，产生 184 组完全重复的
-- 分配历史行（lead_id, action_type, occurred_at, to_owner_user_id, creator 完全相同）。
-- 影响：不影响「接收客资」漏斗（该指标按 leadId 去重），但会使「接收批次数」虚高 184。
--
-- 注意：补齐脚本曾为这些多余行也建了 ASSIGNMENT 快照，因此清理必须两步——
--       先删对应快照，再删多余行，否则会被外键式保护条件挡住而静默不删。
--
-- 保守策略：只删除重复组中 id 最大的一行，保留最早插入的那条；
--           若某行被 zsjos_lead.current_assignment_history_id 指向则跳过，不删除。
--
-- 幂等：可重复执行；删除后不再存在重复组。

SET NAMES utf8mb4;

CREATE TEMPORARY TABLE tmp_dup_receipt_doomed (id bigint PRIMARY KEY);

INSERT INTO tmp_dup_receipt_doomed (id)
SELECT d.doomed FROM (
  SELECT MAX(id) AS doomed
    FROM zsjos_lead_assignment_history
   WHERE action_type IN ('accept','claim','transfer') AND tenant_id = 1
   GROUP BY lead_id, action_type, occurred_at, to_owner_user_id
  HAVING COUNT(*) > 1) d
WHERE NOT EXISTS (SELECT 1 FROM zsjos_lead l WHERE l.current_assignment_history_id = d.doomed);

-- 先删这些行上的快照，解除保护条件
DELETE a FROM zsjos_performance_attribution a
 JOIN tmp_dup_receipt_doomed t ON a.fact_id = t.id
WHERE a.fact_type = 'ASSIGNMENT';

-- 再删多余的历史行
DELETE h FROM zsjos_lead_assignment_history h
 JOIN tmp_dup_receipt_doomed t ON h.id = t.id
WHERE NOT EXISTS (SELECT 1 FROM zsjos_lead l WHERE l.current_assignment_history_id = h.id);

DROP TEMPORARY TABLE tmp_dup_receipt_doomed;
