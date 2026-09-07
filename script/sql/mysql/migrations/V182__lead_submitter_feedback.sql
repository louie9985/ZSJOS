-- UTF-8. V182: immutable sales feedback for Lead submitters.
-- Deployment: new development/production capability, after Core V181.
-- Prerequisites: Lead, System menu/notification/outbox, Infra file schema.
-- Scope: two empty tables, two menu permissions, one in-app template and one rule per existing tenant.
-- No business records, real account grants or business dictionary options are seeded.
-- Repeatability: guarded inserts and CREATE IF NOT EXISTS preserve administrator changes.
-- Rollback: disable permissions/rules; never delete historical replies or bound files.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS zsjos_lead_submitter_feedback (
  id bigint NOT NULL AUTO_INCREMENT,
  lead_id bigint NOT NULL,
  submitter_subject_type varchar(16) NOT NULL,
  submitter_user_id bigint DEFAULT NULL,
  partner_account_id bigint DEFAULT NULL,
  partner_id bigint DEFAULT NULL,
  sales_user_id bigint NOT NULL,
  sales_name_snapshot varchar(128) DEFAULT NULL,
  submitter_name_snapshot varchar(128) DEFAULT NULL,
  feedback text NOT NULL,
  request_version int NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  request_fingerprint varchar(64) NOT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_lead_sales_intent (tenant_id,lead_id,sales_user_id,idempotency_key),
  KEY idx_tenant_lead_created (tenant_id,lead_id,create_time,id),
  KEY idx_tenant_submitter_created (tenant_id,submitter_subject_type,submitter_user_id,create_time),
  KEY idx_tenant_partner_created (tenant_id,partner_account_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客资销售反馈';

-- A null feedback_id is a temporary upload. Only an unexpired upload by the current sales owner
-- for this tenant and Lead may be bound; once bound it is immutable and no longer expires.
CREATE TABLE IF NOT EXISTS zsjos_lead_submitter_feedback_attachment (
  id bigint NOT NULL AUTO_INCREMENT, lead_id bigint NOT NULL,
  feedback_id bigint DEFAULT NULL, file_id bigint NOT NULL, uploader_user_id bigint NOT NULL,
  original_name varchar(255) NOT NULL, content_type varchar(128) NOT NULL, file_size bigint NOT NULL,
  expires_at datetime NOT NULL, sort int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_tenant_file (tenant_id,file_id),
  KEY idx_tenant_feedback (tenant_id,feedback_id,sort),
  KEY idx_tenant_temporary_expiry (tenant_id,feedback_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客资销售反馈附件';

INSERT INTO system_menu
(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT seed.name,seed.permission,3,seed.sort,parent.id,'','','',NULL,0,b'1',b'1',b'1','migration-V182','migration-V182',b'0'
FROM (SELECT MIN(id) id FROM system_menu WHERE permission='zsjos:lead:query' AND type=2 AND deleted=b'0') parent
JOIN (
  SELECT '查看销售反馈' name,'zsjos:lead:submitter-feedback:read' permission,33 sort
  UNION ALL SELECT '回复客资提交人','zsjos:lead:submitter-feedback:create',34
) seed
WHERE parent.id IS NOT NULL AND NOT EXISTS
  (SELECT 1 FROM system_menu existing WHERE existing.permission=seed.permission AND existing.deleted=b'0');

INSERT INTO system_notify_template
(name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status,remark,creator,updater,deleted)
SELECT '销售回复提交人','ZSJOS_LEAD_SUBMITTER_FEEDBACK_CREATED','中世健消息中心',
 'zsjos.lead.submitter_feedback_created','in_app','销售已回复您提交的客资',
 '客资{{lead.no}}收到新的销售反馈','客资{{lead.no}}收到销售反馈：{{feedback.summary}}。请打开客资查看完整反馈及附件。',
 2,'["lead.no","feedback.summary"]',0,'V182 站内反馈通知','migration-V182','migration-V182',b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_notify_template
 WHERE code='ZSJOS_LEAD_SUBMITTER_FEEDBACK_CREATED' AND deleted=b'0');

INSERT INTO system_notify_rule
(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,action_type,status,creator,updater,deleted,tenant_id)
SELECT '销售反馈通知','zsjos.lead.submitter_feedback_created','in_app',template.id,
 '["submitter"]','[]','business_detail',0,'migration-V182','migration-V182',b'0',tenant.id
FROM system_tenant tenant JOIN system_notify_template template
 ON template.code='ZSJOS_LEAD_SUBMITTER_FEEDBACK_CREATED' AND template.deleted=b'0'
WHERE tenant.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM system_notify_rule existing
 WHERE existing.tenant_id=tenant.id AND existing.scene_code='zsjos.lead.submitter_feedback_created' AND existing.deleted=b'0');

INSERT INTO zsjos_schema_version (version,description,checksum,installed_at)
VALUES ('V182','Lead submitter feedback','V182__lead_submitter_feedback.sql',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version (module_code,version,description,checksum,release_version,installed_at)
VALUES ('core','V182','Lead submitter feedback',SHA2('V182__lead_submitter_feedback.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
