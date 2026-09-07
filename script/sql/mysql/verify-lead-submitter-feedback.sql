-- UTF-8. Read-only verification after V182. Run from repository root using an utf8mb4 client.
SET NAMES utf8mb4;
SELECT 'lead_feedback_schema' check_name, IF(
 (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
 AND table_name IN ('zsjos_lead_submitter_feedback','zsjos_lead_submitter_feedback_attachment'))=2
 AND EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V182')
 AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
 AND table_name='zsjos_lead_submitter_feedback' AND index_name='uk_tenant_lead_sales_intent'), 'PASS','FAIL') result;
SELECT 'lead_feedback_relations' check_name, IF(
 NOT EXISTS (SELECT 1 FROM zsjos_lead_submitter_feedback f LEFT JOIN zsjos_lead l
 ON l.id=f.lead_id AND l.tenant_id=f.tenant_id WHERE l.id IS NULL)
 AND NOT EXISTS (SELECT 1 FROM zsjos_lead_submitter_feedback_attachment a LEFT JOIN zsjos_lead_submitter_feedback f
 ON f.id=a.feedback_id AND f.tenant_id=a.tenant_id AND f.lead_id=a.lead_id
 WHERE a.feedback_id IS NOT NULL AND f.id IS NULL), 'PASS','FAIL') result;
SELECT 'lead_feedback_permissions' check_name, IF(
 (SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:lead:submitter-feedback:read',
 'zsjos:lead:submitter-feedback:create') AND deleted=b'0')=2, 'PASS','FAIL') result;
SELECT 'lead_feedback_notification' check_name, IF(EXISTS (SELECT 1 FROM system_notify_template
 WHERE code='ZSJOS_LEAD_SUBMITTER_FEEDBACK_CREATED' AND deleted=b'0')
 AND NOT EXISTS (SELECT 1 FROM system_tenant t WHERE t.deleted=b'0' AND NOT EXISTS
 (SELECT 1 FROM system_notify_rule r WHERE r.tenant_id=t.id AND r.deleted=b'0'
 AND r.scene_code='zsjos.lead.submitter_feedback_created')), 'PASS','FAIL') result;
SELECT permission, name, HEX(name) name_hex FROM system_menu
 WHERE permission IN ('zsjos:lead:submitter-feedback:read','zsjos:lead:submitter-feedback:create') AND deleted=b'0';
