-- ===========================================================================
-- KZ 客资清理(2026-09-16)
--
-- 背景:测试库里的 KZ* 客资全部是 2026-08-08 ~ 09-07 的联调/验收数据
--       (来源昵称:「全链路测试」「E2E0820有效成交A」「自动测试…」等),
--       与备份库的真实业务数据无关。用户要求全部删除。
--
-- 做法:软删除(deleted=1),不是物理 DELETE —— 保留行以便回滚,
--       且业务表都按 deleted 过滤,不删行也不会出现在界面上。
--
-- 依赖链:KZ 客资 -> 意向课程/跟进/分配历史/附件/申诉/回收池事件
--                  -> 商机 -> 订单 -> 订单明细/审批轮次/命令
--                  -> 服务关系 -> 协同群 -> 媒体账号 -> 人档
--       全部为 KZ 客资独占,删除后不影响其它业务。
--
-- 待删行已快照到 zsjos_kz_backup_20260916 库(见 handoff)。
-- 脚本本身只做软删除,可重复执行。
-- ===========================================================================

SET NAMES utf8mb4;

-- ---- 0. 目标 ID 集合(辅助表,脚本末尾删除)------------------------------
DROP TABLE IF EXISTS t_kz_lead;
CREATE TABLE t_kz_lead (id bigint PRIMARY KEY) AS
  SELECT id FROM zsjos_lead WHERE lead_no LIKE 'KZ%';
DROP TABLE IF EXISTS t_kz_person;
CREATE TABLE t_kz_person (person_id bigint PRIMARY KEY) AS
  SELECT DISTINCT person_id FROM zsjos_lead
   WHERE lead_no LIKE 'KZ%' AND person_id IS NOT NULL;
DROP TABLE IF EXISTS t_kz_order;
CREATE TABLE t_kz_order (id bigint PRIMARY KEY) AS
  SELECT id FROM zsjos_order WHERE lead_id IN (SELECT id FROM t_kz_lead);
DROP TABLE IF EXISTS t_kz_opp;
CREATE TABLE t_kz_opp (id bigint PRIMARY KEY) AS
  SELECT id FROM zsjos_opportunity WHERE lead_id IN (SELECT id FROM t_kz_lead);

START TRANSACTION;

-- ---- 1. 叶子表:按 lead_id ------------------------------------------------
UPDATE zsjos_lead_intended_product SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_lead_assignment_history SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_lead_attachment SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_lead_follow_up_record SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_lead_appeal SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_lead_aging_pool_event SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_lead_aging_pool_cycle SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_lead_urge SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_subordinate_sales_audit_log SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_cashback SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);

-- ---- 2. 订单子树 ---------------------------------------------------------
UPDATE zsjos_order_item SET deleted=1 WHERE deleted=0 AND order_id IN
  (SELECT id FROM zsjos_order WHERE lead_id IN
     (SELECT id FROM t_kz_lead));
UPDATE zsjos_order_approval_round SET deleted=1 WHERE deleted=0 AND order_id IN
  (SELECT id FROM zsjos_order WHERE lead_id IN
     (SELECT id FROM t_kz_lead));
UPDATE zsjos_order_command SET deleted=1 WHERE deleted=0 AND order_id IN
  (SELECT id FROM zsjos_order WHERE lead_id IN
     (SELECT id FROM t_kz_lead));

-- ---- 3. 商机子树 ---------------------------------------------------------
UPDATE zsjos_opportunity_follow_up_record SET deleted=1 WHERE deleted=0 AND opportunity_id IN
  (SELECT id FROM zsjos_opportunity WHERE lead_id IN
     (SELECT id FROM t_kz_lead));
UPDATE zsjos_opportunity SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);
UPDATE zsjos_order SET deleted=1 WHERE deleted=0 AND lead_id IN
  (SELECT id FROM t_kz_lead);

-- ---- 4. 人档子树 ---------------------------------------------------------
UPDATE zsjos_service_relation SET deleted=1 WHERE deleted=0 AND person_id IN
  (SELECT person_id FROM t_kz_person);
UPDATE zsjos_person_contact_claim SET deleted=1 WHERE deleted=0 AND person_id IN
  (SELECT person_id FROM t_kz_person);
UPDATE zsjos_collaboration_group SET deleted=1 WHERE deleted=0 AND student_person_id IN
  (SELECT person_id FROM t_kz_person);
UPDATE zsjos_media_account_student_link SET deleted=1 WHERE deleted=0 AND student_person_id IN
  (SELECT person_id FROM t_kz_person);
UPDATE zsjos_partner_student_link SET deleted=1 WHERE deleted=0 AND student_person_id IN
  (SELECT person_id FROM t_kz_person);
UPDATE zsjos_media_account SET deleted=1 WHERE deleted=0 AND student_person_id IN
  (SELECT person_id FROM t_kz_person);

-- ---- 5. 主表 -------------------------------------------------------------
UPDATE zsjos_lead SET deleted=1, assignment_status='closed'
 WHERE deleted=0 AND lead_no LIKE 'KZ%';
UPDATE zsjos_person SET deleted=1
 WHERE deleted=0 AND id IN (SELECT person_id FROM t_kz_person);

COMMIT;

DROP TABLE IF EXISTS t_kz_lead, t_kz_person, t_kz_order, t_kz_opp;
