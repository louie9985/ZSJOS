-- UTF-8. V257: 教务自拓，复用客资及成交流程。
-- Deployment: additive upgrade after V256; fresh installs run the existing baseline then numbered migrations.
-- Scope: six nullable identity columns one Workbench menu and the exact V080 default provider wording; no historical identity backfill or role grants.
-- Repeatable: guarded columns; menu inserted only when its permission does not exist.
-- Rollback: roll back application and disable menu via administration; retain columns/snapshots. No destructive rollback.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v257_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v257_apply()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND column_name='owner_identity') THEN
    ALTER TABLE `zsjos_lead` ADD COLUMN `owner_identity` varchar(32) DEFAULT NULL COMMENT '负责人业务身份：sales/education；历史空值不推断';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_assignment_history' AND column_name='owner_identity_snapshot') THEN
    ALTER TABLE `zsjos_lead_assignment_history` ADD COLUMN `owner_identity_snapshot` varchar(32) DEFAULT NULL COMMENT '本次归属负责人身份快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='formal_owner_identity') THEN
    ALTER TABLE `zsjos_order` ADD COLUMN `formal_owner_identity` varchar(32) DEFAULT NULL COMMENT '成交归属业务身份快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_complaint' AND column_name='owner_identity_snapshot') THEN
    ALTER TABLE `zsjos_lead_complaint` ADD COLUMN `owner_identity_snapshot` varchar(32) DEFAULT NULL COMMENT '被投诉负责人身份快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_follow_up_record' AND column_name='owner_identity_snapshot') THEN
    ALTER TABLE `zsjos_lead_follow_up_record` ADD COLUMN `owner_identity_snapshot` varchar(32) DEFAULT NULL COMMENT '跟进时负责人身份快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_opportunity_follow_up_record' AND column_name='owner_identity_snapshot') THEN
    ALTER TABLE zsjos_opportunity_follow_up_record ADD COLUMN owner_identity_snapshot varchar(32) DEFAULT NULL COMMENT '有效客资跟进负责人身份快照';
  END IF;
END$$
DELIMITER ;
CALL zsjos_v257_apply();
DROP PROCEDURE zsjos_v257_apply;

INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,workbench_render_mode,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '教务自拓','zsjos:lead:education-self-sourced:create',2,15,id,'leads/education-self-sourced','ep:edit','zsjos/leadEducationSelfSourced/index','LeadEducationSelfSourcedPage','native',0,b'1',b'0',b'1','V257',NOW(),'V257',NOW(),b'0'
FROM system_menu root
WHERE root.path='/zsjos' AND root.parent_id=0 AND root.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission='zsjos:lead:education-self-sourced:create' AND existing.deleted=b'0');



-- Replace only exact V080 default content AND summary, regardless of maintenance metadata.
-- Template name/status, recipient rules, customized text and delivered history remain unchanged.
UPDATE system_notify_template
SET summary='{{operator.name}}（{{lead.submitterIdentityLabel}}）提交客资{{lead.no}}（客资编号），已关联你为客资来源。',
    content='{{operator.name}}（{{lead.submitterIdentityLabel}}）提交客资{{lead.no}}（客资编号），已关联你为客资来源。',
    params='["operator.name","lead.submitterIdentityLabel","lead.no"]',updater='V257',update_time=NOW()
WHERE code='ZSJOS_LEAD_SOURCE_LINKED' AND deleted=b'0'
 AND content='{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。'
 AND summary=content;

INSERT IGNORE INTO zsjos_schema_version(version,description,checksum)
VALUES ('V257','教务自拓与成交身份快照','education-self-sourced-v1');
