-- =============================================================================
-- 补齐老库 (parttimecrm) 的「下次待跟进」提醒到新系统 zsjos
--
-- 背景：
--   * 跟进历史 (followup_record 18980 条) 已由 2026-09-16 的 legacy-migration 全部导入，
--     idempotency_key = 'legacy-followup-<老id>'，本次不动。
--   * 老库 followup_reminder 里 status='pending' 的 3357 条从未导入过，
--     导致销售在新系统待办里看不到历史欠下的跟进。
--
-- 依赖库：oldcrm_scratch（由 parttimecrm-cst+0800-20260920-222103.sql.gz 灌入）
--         zsjos（新系统）
--
-- 关联口径（均已全量验证）：
--   lead   : 老 leads_lead.lead_no  =  新 zsjos_lead.lead_no   （4994/4994，不可用 id 偏移）
--   销售   : 老 employees_employee_profile.phone = system_users.mobile，回退 username（3357/3357）
--   跟进记录: 新 zsjos_lead_follow_up_record.idempotency_key = 'legacy-followup-<老 followup_id>'
--
-- 幂等：idempotency_key = 'lead-follow-up-reminder:lead:<新跟进记录id>'
--       zsjos_business_task 上有 uk_tenant_idempotency(tenant_id, idempotency_key)，
--       重复执行会被唯一键吞掉（脚本用 NOT EXISTS 预过滤 + INSERT IGNORE 双保险）。
--
-- 执行：先跑 0)预览，再跑 1)备份，最后 2)apply
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0) 预览：迁移计划与逾期口径分布（apply 前后都应跑，apply 后 actionable 应为 0）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS oldcrm_scratch.mig_plan;
CREATE TABLE oldcrm_scratch.mig_plan AS
SELECT r.id                AS old_reminder_id,
       r.followup_id       AS old_followup_id,
       nf.id               AS new_followup_id,
       nl.id               AS new_lead_id,
       nl.lead_no,
       nl.status           AS lead_status,
       nl.assignment_status,
       nl.current_assignment_history_id,
       COALESCE(nl.owner_user_id, m.new_user_id) AS new_assignee_id,
       r.sales_id          AS old_sales_id,
       r.remind_at         AS old_remind_at,
       r.overdue_at        AS old_overdue_at,
       r.created_at        AS old_created_at,
       -- 是否还有意义：新系统 canFollow() 只接受 valid/won，或 submitted+owned+有分配历史
       CASE WHEN nl.status IN ('valid', 'won') THEN 1
            WHEN nl.status = 'submitted' AND nl.assignment_status = 'owned'
                 AND nl.current_assignment_history_id IS NOT NULL THEN 1
            ELSE 0 END      AS actionable,
       -- 是否已被新系统的真实业务取代（老提醒之后销售又跟过）
       CASE WHEN EXISTS (SELECT 1 FROM zsjos.zsjos_lead_follow_up_record f
                          WHERE f.lead_id = nl.id
                            AND f.idempotency_key NOT LIKE 'legacy-followup-%') THEN 1
            ELSE 0 END      AS superseded,
       -- 新系统里是否已有 pending 提醒
       CASE WHEN EXISTS (SELECT 1 FROM zsjos.zsjos_business_task bt
                          WHERE bt.task_type = 'lead_follow_up_reminder'
                            AND bt.status = 'pending' AND bt.biz_id = nl.id) THEN 1
            ELSE 0 END      AS already_pending
FROM oldcrm_scratch.followup_reminder r
JOIN oldcrm_scratch.leads_lead ol ON ol.id = r.lead_id
JOIN zsjos.zsjos_lead nl          ON nl.lead_no = ol.lead_no
LEFT JOIN zsjos.zsjos_lead_follow_up_record nf
       ON nf.idempotency_key = CONCAT('legacy-followup-', r.followup_id)
LEFT JOIN (SELECT o.id AS old_emp_id, COALESCE(u1.id, u2.id) AS new_user_id
             FROM oldcrm_scratch.employees_employee_profile o
             LEFT JOIN system_users u1 ON u1.mobile   = o.phone
             LEFT JOIN system_users u2 ON u2.username = o.phone) m
       ON m.old_emp_id = r.sales_id
WHERE r.reminder_type = 'next_follow'
  AND r.status = 'pending'
  AND r.handled_at IS NULL
  AND r.closed_at IS NULL;

SELECT '计划总数' k, COUNT(*) v FROM oldcrm_scratch.mig_plan
UNION ALL SELECT '待写入(可跟进且未被取代)', COUNT(*) FROM oldcrm_scratch.mig_plan
          WHERE actionable = 1 AND superseded = 0 AND already_pending = 0 AND new_followup_id IS NOT NULL
UNION ALL SELECT '跳过-线索已失效(invalid/converted)', COUNT(*) FROM oldcrm_scratch.mig_plan WHERE actionable = 0
UNION ALL SELECT '跳过-已有更新的跟进', COUNT(*) FROM oldcrm_scratch.mig_plan WHERE superseded = 1
UNION ALL SELECT '跳过-新系统已有待办', COUNT(*) FROM oldcrm_scratch.mig_plan WHERE already_pending = 1
UNION ALL SELECT '异常-无关联跟进记录', COUNT(*) FROM oldcrm_scratch.mig_plan WHERE new_followup_id IS NULL
UNION ALL SELECT '异常-无归属人', COUNT(*) FROM oldcrm_scratch.mig_plan WHERE new_assignee_id IS NULL;

-- -----------------------------------------------------------------------------
-- 1) 备份
-- -----------------------------------------------------------------------------
-- CREATE TABLE zsjos.zsjos_business_task_bak_20260928 LIKE zsjos.zsjos_business_task;
-- INSERT INTO zsjos.zsjos_business_task_bak_20260928 SELECT * FROM zsjos.zsjos_business_task;

-- -----------------------------------------------------------------------------
-- 2) Apply
-- -----------------------------------------------------------------------------
-- 逾期口径（dump 快照 = 2026-09-20 22:21:03，今天 = 2026-09-28）：
--   A 快照时真逾期 : due_at 保留老 overdue_at，维持真实逾期天数，销售看得到欠账
--   B 空档造成逾期 : 快照时未到期但今天已过期 → 重锚到今天 now()，不背 8 天无效逾期
--   C 未来         : 保留老 overdue_at
--   脏数据         : 超过 1 年的（最远 2035）按 创建时间+7天 折回
UPDATE oldcrm_scratch.mig_plan
SET old_overdue_at = CASE
        WHEN old_overdue_at > NOW() + INTERVAL 1 YEAR
             THEN DATE_ADD(LEAST(old_created_at, NOW()), INTERVAL 7 DAY)
        WHEN old_overdue_at < '2026-09-20 22:21:03' THEN old_overdue_at   -- A
        WHEN old_overdue_at < NOW()                  THEN NOW()           -- B
        ELSE old_overdue_at                                               -- C
    END;

INSERT IGNORE INTO zsjos.zsjos_business_task
    (task_type, biz_type, biz_id, status, assignee_type, assignee_id,
     title_snapshot, summary_snapshot, action_code, due_at, remind_at,
     payload, idempotency_key, version, creator, updater,
     create_time, update_time, deleted, tenant_id)
SELECT 'lead_follow_up_reminder', 'lead', p.new_lead_id, 'pending', 'user', p.new_assignee_id,
       CONCAT('跟进提醒：', p.lead_no), NULL, 'OPEN_LEAD_FOLLOW_UP',
       p.old_overdue_at, p.old_remind_at,
       JSON_OBJECT('followUpRecordScope', 'lead', 'followUpRecordId', p.new_followup_id),
       CONCAT('lead-follow-up-reminder:lead:', p.new_followup_id),
       0, 'legacy-migration', 'legacy-migration',
       NOW(), NOW(), b'0', 1
FROM oldcrm_scratch.mig_plan p
WHERE p.actionable = 1
  AND p.superseded = 0
  AND p.already_pending = 0
  AND p.new_followup_id IS NOT NULL
  AND p.new_assignee_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 3) 回填 zsjos_lead 上的冗余字段（legacy 导入时漏写，列表/详情会显示错）
--    只补「不晚于已有值」的保守写法，不覆盖新业务的真实跟进
-- -----------------------------------------------------------------------------
UPDATE zsjos.zsjos_lead l
JOIN (SELECT f.lead_id,
             MAX(f.occurred_at) AS max_occurred,
             COUNT(*)           AS fu_cnt,
             MAX(f.id)          AS last_record_id
        FROM zsjos.zsjos_lead_follow_up_record f
       WHERE f.idempotency_key LIKE 'legacy-followup-%'
       GROUP BY f.lead_id) t ON t.lead_id = l.id
SET l.follow_up_count          = t.fu_cnt,
    l.last_follow_up_at        = GREATEST(COALESCE(l.last_follow_up_at, t.max_occurred), t.max_occurred),
    l.last_follow_up_record_id = COALESCE(l.last_follow_up_record_id, t.last_record_id)
WHERE l.follow_up_count <> t.fu_cnt
   OR l.last_follow_up_at IS NULL
   OR l.last_follow_up_record_id IS NULL;

-- 首次跟进时间也漏了（影响首跟考核）
UPDATE zsjos.zsjos_lead l
JOIN (SELECT f.lead_id, MIN(f.occurred_at) AS first_occurred
        FROM zsjos.zsjos_lead_follow_up_record f
       WHERE f.idempotency_key LIKE 'legacy-followup-%'
       GROUP BY f.lead_id) t ON t.lead_id = l.id
SET l.current_assignment_first_follow_up_at = t.first_occurred
WHERE l.current_assignment_first_follow_up_at IS NULL;

-- -----------------------------------------------------------------------------
-- 4) 复核
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS 新系统待跟进总数
FROM zsjos.zsjos_business_task
WHERE task_type = 'lead_follow_up_reminder' AND status = 'pending';

SELECT CASE WHEN due_at < NOW() THEN '已逾期'
            WHEN due_at < CURDATE() + INTERVAL 1 DAY THEN '今天'
            ELSE '未来' END AS 桶, COUNT(*) AS 条数
FROM zsjos.zsjos_business_task
WHERE task_type = 'lead_follow_up_reminder' AND status = 'pending'
GROUP BY 1;
