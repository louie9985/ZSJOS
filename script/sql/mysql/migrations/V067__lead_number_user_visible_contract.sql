-- V067: use Lead business numbers on every system-owned user-visible surface.
-- Dependency/order: apply after V066. V054 must already provide non-null zsjos_lead.lead_no.
-- Data scope: updates only untouched System default notification templates and untouched ZSJOS
-- read-only BPM forms. Administrator-created or edited templates/forms and historical messages are preserved.
-- Repeatability: replacements are idempotent and version rows are upserted.
-- Rollback limitation: newly rendered messages and newly started BPM instances retain leadNo snapshots.
-- Reverting presentation should use another forward migration; internal leadId relationships are unchanged.

UPDATE `system_notify_template`
SET `title`=REPLACE(`title`,'{{lead.id}}','{{lead.no}}'),
    `summary`=REPLACE(`summary`,'{{lead.id}}','{{lead.no}}'),
    `content`=REPLACE(`content`,'{{lead.id}}','{{lead.no}}'),
    `params`=REPLACE(`params`,'"lead.id"','"lead.no"'),
    `updater`='migration-V067',`update_time`=NOW()
WHERE `deleted`=b'0' AND `scene_code` LIKE 'zsjos.lead.%'
  AND (`title` LIKE '%{{lead.id}}%' OR `summary` LIKE '%{{lead.id}}%'
       OR `content` LIKE '%{{lead.id}}%' OR `params` LIKE '%"lead.id"%')
  AND `creator`=`updater`
  AND `creator` IN ('quick-init','migration-V011','migration-V016','migration-V031',
                    'migration-V040','migration-V056','migration-V066');

SET @zsjos_bpm_form_conf = '{"form":{"labelPosition":"right","labelWidth":"120px","size":"default"},"submitBtn":false,"resetBtn":false}';
SET @zsjos_bpm_field_appeal_id = '{"type":"input","field":"appealId","title":"申诉编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_order_id = '{"type":"input","field":"orderId","title":"订单编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_lead_no = '{"type":"input","field":"leadNo","title":"客资编号","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_round_no = '{"type":"input","field":"roundNo","title":"审批轮次","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';
SET @zsjos_bpm_field_review_stage = '{"type":"input","field":"reviewStage","title":"复核阶段","props":{"disabled":true,"readonly":true},"hidden":false,"display":true}';

UPDATE `bpm_form`
SET `conf`=@zsjos_bpm_form_conf,
    `fields`=JSON_ARRAY(@zsjos_bpm_field_appeal_id,@zsjos_bpm_field_lead_no,
                        @zsjos_bpm_field_round_no,@zsjos_bpm_field_review_stage),
    `updater`='migration-V067',`update_time`=NOW()
WHERE `remark`='zsjos-system-form:lead-appeal-review' AND `deleted`=b'0'
  AND `creator`=`updater` AND `creator` IN ('quick-init','migration-V024');

UPDATE `bpm_form`
SET `conf`=@zsjos_bpm_form_conf,
    `fields`=JSON_ARRAY(@zsjos_bpm_field_order_id,@zsjos_bpm_field_lead_no,@zsjos_bpm_field_round_no),
    `updater`='migration-V067',`update_time`=NOW()
WHERE `remark`='zsjos-system-form:sales-order-dual-approval' AND `deleted`=b'0'
  AND `creator`=`updater` AND `creator` IN ('quick-init','migration-V024');

ALTER TABLE `zsjos_lead`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '内部客资ID';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V067','Lead number user-visible contract','V067__lead_number_user_visible_contract.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V067','Lead number user-visible contract',
        SHA2('V067__lead_number_user_visible_contract.sql',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
