-- UTF-8. LOCAL DEVELOPMENT ONLY. Authorized multi-candidate fixture, 2026-11-06.
-- Target ruoyi-vue-pro, tenant 1. Outside bootstrap and migration chains.
-- Prerequisites: the two source Leads and their live Persons exist.
-- Exact scope: 2 closed Leads for source KZ202610291544560042 and 4 for KZ202610291428350032.
-- Existing Person/Lead/order data is unchanged. No lifecycle/history/task relations are copied.
-- Execute inside a failure-controlled transaction; assert candidate counts 3/5 before commit.
-- Replay inserts zero rows. No version ledger changes or DDL.
-- Recovery: inspect references then remove only KZTEST20261106THREE01/02 and KZTEST20261106MANY01..04.
-- Orders already using their snapshots retain historical evidence.
SET NAMES utf8mb4;
INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106THREE01',l.person_id,l.submitted_name,l.submitted_mobile,l.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：复购订单多候选客资选择；来源 KZ202610291544560042',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610291544560042'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106THREE01');

INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106THREE02',l.person_id,l.submitted_name,l.submitted_mobile,l.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：复购订单多候选客资选择；来源 KZ202610291544560042',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610291544560042'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106THREE02');

INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106MANY01',l.person_id,l.submitted_name,l.submitted_mobile,l.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：复购订单多候选客资选择；来源 KZ202610291428350032',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610291428350032'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106MANY01');

INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106MANY02',l.person_id,l.submitted_name,l.submitted_mobile,l.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：复购订单多候选客资选择；来源 KZ202610291428350032',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610291428350032'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106MANY02');

INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106MANY03',l.person_id,l.submitted_name,l.submitted_mobile,l.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：复购订单多候选客资选择；来源 KZ202610291428350032',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610291428350032'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106MANY03');

INSERT INTO zsjos_lead (
  lead_no,person_id,submitted_name,submitted_mobile,submitted_wechat_id,
  source_type,submission_source_type,source_user_id,source_provider_user_id,source_provider_recorded,
  source_dept_id,partner_id,source_channel_id,source_channel_label_snapshot,
  province_code,province_name,city_code,city_name,lead_category,lead_category_label_snapshot,
  owner_user_id,owner_identity,dispatch_mode,status,assignment_status,
  submitted_at,closed_at,close_reason,remark,tenant_id,creator,updater
)
SELECT 'KZTEST20261106MANY04',l.person_id,l.submitted_name,l.submitted_mobile,l.submitted_wechat_id,
  l.source_type,l.submission_source_type,l.source_user_id,l.source_provider_user_id,l.source_provider_recorded,
  l.source_dept_id,l.partner_id,l.source_channel_id,l.source_channel_label_snapshot,
  l.province_code,l.province_name,l.city_code,l.city_name,l.lead_category,l.lead_category_label_snapshot,
  l.owner_user_id,l.owner_identity,l.dispatch_mode,'closed','owned',
  NOW(),NOW(),'本地多候选录单测试','本地开发测试：复购订单多候选客资选择；来源 KZ202610291428350032',1,'local-test-fixture','local-test-fixture'
FROM zsjos_lead l
JOIN zsjos_person p ON p.id=l.person_id AND p.tenant_id=1 AND p.deleted=b'0'
WHERE l.tenant_id=1 AND l.deleted=b'0' AND l.lead_no='KZ202610291428350032'
  AND NOT EXISTS (SELECT 1 FROM zsjos_lead fixture WHERE fixture.tenant_id=1 AND fixture.lead_no='KZTEST20261106MANY04');
