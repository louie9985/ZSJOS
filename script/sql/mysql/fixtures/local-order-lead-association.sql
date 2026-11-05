-- UTF-8. LOCAL DEVELOPMENT FIXTURE ONLY. Explicitly authorized 2026-11-05.
-- Target: ruoyi-vue-pro, tenant 1. Not part of bootstrap/migrations, no ledger changes.
-- Prerequisite: source Lead KZ202610291728080050 and its live Person exist.
-- Run in a failure-controlled transaction, validate source, candidate count and UTF-8 HEX before commit.
-- Repeated execution inserts zero rows when the exact fixture key already exists.
-- Scope: one new closed Lead with the source Person/contact/profile. Existing rows are not updated.
-- No workflow, task, follow-up, assignment-history or order relations are copied.
-- Recovery: explicitly inspect references before removing only KZTEST20261105MULTI01 in tenant 1.
-- Once used in an order snapshot, cleanup must preserve historical evidence.
SET NAMES utf8mb4;
INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261105MULTI01',l.person_id,l.submitted_name,l.submitted_mobile,l.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：复购订单多候选客资选择；来源 KZ202610291728080050',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610291728080050'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261105MULTI01');
