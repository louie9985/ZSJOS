-- V095 student contact extension BPM approval form.
-- Dependencies: V094, bpm_form, system_tenant, and the BPM process asset
-- zsjos_student_contact_extension/1.0.0.
-- Data scope: one read-only snapshot form definition per active tenant; no business rows.
-- Repeatability: the stable marker prevents duplicate forms and preserves administrator edits.
-- Rollback limitation: disable the form and retain process definitions, instances, tasks, and audit history.

SET NAMES utf8mb4;

SET @zsjos_bpm_form_conf = '{"form":{"labelPosition":"right","labelWidth":"140px","size":"default"},"submitBtn":false,"resetBtn":false}';
SET @zsjos_extension_id = '{"type":"input","field":"extensionId","title":"延期申请编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_service_relation_id = '{"type":"input","field":"serviceRelationId","title":"服务关系编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_original_due_at = '{"type":"input","field":"originalDueAt","title":"原允许截止时间","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_requested_due_at = '{"type":"input","field":"requestedDueAt","title":"申请联系时间","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_reason_value = '{"type":"input","field":"reasonValue","title":"延期原因值","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_reason_label = '{"type":"input","field":"reasonLabel","title":"延期原因","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_description = '{"type":"textarea","field":"description","title":"延期说明","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_attachments = '{"type":"input","field":"attachmentFileIds","title":"延期附件 ID","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_applicant = '{"type":"input","field":"applicantUserId","title":"申请人用户 ID","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_submitted_at = '{"type":"input","field":"submittedAt","title":"申请时间","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';

INSERT INTO `bpm_form`
(`name`,`status`,`conf`,`fields`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.name,0,@zsjos_bpm_form_conf,seed.fields,seed.marker,
       'migration-V095',NOW(),'migration-V095',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
CROSS JOIN (
  SELECT '学员联系延期审批表单' name,
         JSON_ARRAY(JSON_EXTRACT(@zsjos_extension_id,'$'),JSON_EXTRACT(@zsjos_service_relation_id,'$'),
                    JSON_EXTRACT(@zsjos_original_due_at,'$'),JSON_EXTRACT(@zsjos_requested_due_at,'$'),
                    JSON_EXTRACT(@zsjos_reason_value,'$'),JSON_EXTRACT(@zsjos_reason_label,'$'),
                    JSON_EXTRACT(@zsjos_description,'$'),JSON_EXTRACT(@zsjos_attachments,'$'),
                    JSON_EXTRACT(@zsjos_applicant,'$'),JSON_EXTRACT(@zsjos_submitted_at,'$')) fields,
         'zsjos-system-form:student-contact-extension' marker
) seed
WHERE tenant.deleted=b'0' AND tenant.status=0
  AND NOT EXISTS (
    SELECT 1 FROM `bpm_form` existing
    WHERE existing.tenant_id=tenant.id AND existing.remark=seed.marker AND existing.deleted=b'0'
  );

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V095','Add student contact extension BPM form','student-contact-extension-bpm-form-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V095','Add student contact extension BPM form',
        SHA2('student-contact-extension-bpm-form-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
