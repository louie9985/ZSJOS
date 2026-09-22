-- UTF-8. V274 follows the published V273 migration and adds the reply side of Lead submitter assistance.
-- Scope: schema columns, history read permission, reply defaults and scoped sales notification alignment.
-- Active local development correction: includes sales-notification-alignment.sql after reply defaults.
-- That script seeds payment-paid defaults, missing sales WeCom rules and one exact system template repair.
-- Retain deployed historical checksums; a shared/deployed rollout requires separate upgrade review.
-- No role assignments or business rows are changed. Repeatable guarded DDL/DML.
SET NAMES utf8mb4;

SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_submitter_assist_request' AND column_name='status')=0,
  'ALTER TABLE zsjos_lead_submitter_assist_request ADD COLUMN status varchar(32) NOT NULL DEFAULT ''pending'' AFTER idempotency_key, ADD COLUMN response_remark varchar(2000) DEFAULT NULL, ADD COLUMN response_attachment_snapshots_json json DEFAULT NULL, ADD COLUMN responder_user_id_snapshot bigint DEFAULT NULL, ADD COLUMN responder_name_snapshot varchar(128) DEFAULT NULL, ADD COLUMN responded_at datetime DEFAULT NULL, ADD COLUMN version int NOT NULL DEFAULT 0', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = (SELECT IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead_submitter_assist_request' AND index_name='idx_tenant_lead_status')=0,
  'ALTER TABLE zsjos_lead_submitter_assist_request ADD KEY idx_tenant_lead_status (tenant_id,lead_id,status,id)', 'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '协助历史','zsjos:lead:submitter-assist:read',3,33,parent.id,'','','',NULL,0,b'1',b'1',b'1','migration-V274',NOW(),'migration-V274',NOW(),b'0'
FROM system_menu parent WHERE parent.permission='zsjos:lead:query' AND parent.type=2 AND parent.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu existing WHERE existing.permission='zsjos:lead:submitter-assist:read' AND existing.deleted=b'0') ORDER BY parent.id LIMIT 1;

INSERT INTO system_notify_template (name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,create_time,updater,update_time,deleted)
SELECT '协助申请已回复','ZSJOS_LEAD_SUBMITTER_ASSIST_REPLIED','中世健消息中心','zsjos.lead.submitter_assist_replied','in_app','协助申请已回复','客资{{lead.no}}的协助申请已回复','客资{{lead.no}}的协助申请已回复：{{assist.response}}',2,'["lead.no","assist.response"]',0,'V274 默认模板','migration-V274',NOW(),'migration-V274',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template WHERE code='ZSJOS_LEAD_SUBMITTER_ASSIST_REPLIED' AND deleted=b'0');

INSERT INTO system_notify_rule (name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT '协助申请回复通知','zsjos.lead.submitter_assist_replied','in_app',template.id,'["requester"]','[]','business_detail',0,'migration-V274',NOW(),'migration-V274',NOW(),b'0',tenant.id
FROM system_tenant tenant JOIN system_notify_template template ON template.code='ZSJOS_LEAD_SUBMITTER_ASSIST_REPLIED' AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule existing WHERE existing.tenant_id=tenant.id AND existing.scene_code='zsjos.lead.submitter_assist_replied' AND existing.deleted=b'0');

-- Complete current sales WeCom defaults without overwriting tenant choices.
SOURCE script/sql/mysql/sales-notification-alignment.sql;

INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V274','Bidirectional submitter assistance','V274__lead_submitter_assist_bidirectional.sql',NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
