-- V024 read-only BPM forms for system-started ZSJOS workflows.
-- Dependencies: V015, V020, V023, BPM form tables, System tenant data, and schema-version tables.
-- Data scope: two enabled technical form definitions for every active tenant; no business rows are created or changed.
-- Repeatability: stable system-form markers prevent duplicate active forms and administrator edits are preserved.
-- Rollback limitation: disable the forms and retain model references, process definitions, instances, tasks, and audit history.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL, `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL, `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @zsjos_bpm_form_conf = '{"form":{"labelPosition":"right","labelWidth":"120px","size":"default"},"submitBtn":false,"resetBtn":false}';
SET @zsjos_bpm_field_appeal_id = '{"type":"input","field":"appealId","title":"申诉编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_order_id = '{"type":"input","field":"orderId","title":"订单编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_lead_id = '{"type":"input","field":"leadId","title":"客资编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_round_no = '{"type":"input","field":"roundNo","title":"审批轮次","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_review_stage = '{"type":"input","field":"reviewStage","title":"复核阶段","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';

INSERT INTO `bpm_form`
(`name`,`status`,`conf`,`fields`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT seed.name,0,@zsjos_bpm_form_conf,seed.fields,seed.marker,
       'migration-V024',NOW(),'migration-V024',NOW(),b'0',tenant.id
FROM `system_tenant` tenant
CROSS JOIN (
  SELECT '客资申诉流程关联信息' name,
         JSON_ARRAY(@zsjos_bpm_field_appeal_id,@zsjos_bpm_field_lead_id,
                    @zsjos_bpm_field_round_no,@zsjos_bpm_field_review_stage) fields,
         'zsjos-system-form:lead-appeal-review' marker
  UNION ALL
  SELECT '成交会签流程关联信息',
         JSON_ARRAY(@zsjos_bpm_field_order_id,@zsjos_bpm_field_lead_id,@zsjos_bpm_field_round_no),
         'zsjos-system-form:sales-order-dual-approval'
) seed
WHERE tenant.deleted=b'0' AND tenant.status=0
  AND NOT EXISTS (
    SELECT 1 FROM `bpm_form` existing
    WHERE existing.tenant_id=tenant.id AND existing.remark=seed.marker AND existing.deleted=b'0'
  );

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V024','Add read-only BPM forms for ZSJOS workflows','zsjos-bpm-readonly-forms-v1');

INSERT IGNORE INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V024','Add read-only BPM forms for ZSJOS workflows',
        SHA2('zsjos-bpm-readonly-forms-v1',256),'legacy',NOW());
