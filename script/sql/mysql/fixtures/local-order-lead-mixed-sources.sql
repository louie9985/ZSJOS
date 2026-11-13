-- UTF-8. LOCAL DEVELOPMENT ONLY. Authorized mixed-source fixture, 2026-11-06.
-- Target ruoyi-vue-pro tenant 1. Prerequisites: three documented source Leads with live Persons.
-- Target customer is the Person of KZ202610291226500027 (sales self-sourced).
-- Add SOURCE01 with media source metadata and SOURCE02 with Partner source metadata.
-- Contacts and Person belong to the target, source references reuse existing development examples.
-- No original rows, source ownership, histories, orders or tasks are modified.
-- Failure-controlled transaction must assert three distinct sources and no active repurchase before commit.
-- Replay inserts zero rows. Outside bootstrap/migrations, no ledger changes.
-- Recovery: inspect references then clean only KZTEST20261106SOURCE01 and KZTEST20261106SOURCE02.
SET NAMES utf8mb4;
INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106SOURCE01',d.person_id,d.submitted_name,d.submitted_mobile,d.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：多来源客资卡牌；来源样本 KZ202610301138250012',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_lead d ON d.tenant_id=1 AND d.deleted=b'0' AND d.lead_no='KZ202610291226500027'
JOIN zsjos_person target ON target.id=d.person_id AND target.tenant_id=1 AND target.deleted=b'0'
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610301138250012'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106SOURCE01');

INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106SOURCE02',d.person_id,d.submitted_name,d.submitted_mobile,d.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：多来源客资卡牌；来源样本 KZ202610301306260017',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_lead d ON d.tenant_id=1 AND d.deleted=b'0' AND d.lead_no='KZ202610291226500027'
JOIN zsjos_person target ON target.id=d.person_id AND target.tenant_id=1 AND target.deleted=b'0'
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610301306260017'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106SOURCE02');
