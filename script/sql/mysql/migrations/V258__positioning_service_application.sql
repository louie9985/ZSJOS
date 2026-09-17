-- UTF-8. New independent positioning/application feature; prerequisite: V257 schema.
-- Scope: nullable submission account, three relationship/audit tables, missing application menu metadata.
-- No business deletion or role grants. Backfill only absent relations using existing effective submissions.
-- Run after prior migrations, before new backend. Repeatable; do not drop new tables to roll back after use.
-- Existing card/submission/account rows remain intact; ambiguous service cards require an explicit master choice.
SET NAMES utf8mb4;
ALTER TABLE zsjos_positioning_card_submission MODIFY account_id bigint NULL;
CREATE TABLE IF NOT EXISTS zsjos_positioning_service_card (
  id bigint NOT NULL AUTO_INCREMENT, service_relation_id bigint NOT NULL, student_person_id bigint NOT NULL,
  card_id bigint NOT NULL, version int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_tenant_service(tenant_id,service_relation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS zsjos_positioning_application (
  id bigint NOT NULL AUTO_INCREMENT, account_id bigint NOT NULL, submission_id bigint NOT NULL,
  applied_by bigint NULL, version int NOT NULL DEFAULT 1,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_tenant_account(tenant_id,account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS zsjos_positioning_application_log (
  id bigint NOT NULL AUTO_INCREMENT, account_id bigint NOT NULL, previous_submission_id bigint NULL,
  submission_id bigint NOT NULL, applied_by bigint NULL, expected_version int NOT NULL,
  idempotency_key varchar(64) COLLATE utf8mb4_bin NOT NULL,
  creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_tenant_key(tenant_id,idempotency_key), KEY idx_account(tenant_id,account_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO zsjos_positioning_service_card(service_relation_id,student_person_id,card_id,tenant_id)
SELECT c.service_relation_id,MIN(c.student_person_id),MIN(c.id),c.tenant_id FROM zsjos_positioning_card c
WHERE c.deleted=0 AND c.service_relation_id IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM zsjos_positioning_service_card m WHERE m.tenant_id=c.tenant_id AND m.service_relation_id=c.service_relation_id)
GROUP BY c.tenant_id,c.service_relation_id HAVING COUNT(*)=1;

INSERT INTO zsjos_positioning_application(account_id,submission_id,applied_by,tenant_id)
SELECT ranked.account_id,ranked.id,NULL,ranked.tenant_id FROM (
 SELECT s.*,ROW_NUMBER() OVER(PARTITION BY s.tenant_id,s.account_id ORDER BY s.submitted_at DESC,s.id DESC) rn
 FROM zsjos_positioning_card_submission s JOIN zsjos_positioning_card c ON c.id=s.card_id AND c.tenant_id=s.tenant_id AND c.deleted=0
 JOIN zsjos_media_account a ON a.id=s.account_id AND a.tenant_id=s.tenant_id AND a.deleted=0
 WHERE s.deleted=0 AND (s.status='confirmed' OR (s.status='student_agreed' AND c.status<>'archived'))
) ranked WHERE rn=1 AND NOT EXISTS(SELECT 1 FROM zsjos_positioning_application a WHERE a.tenant_id=ranked.tenant_id AND a.account_id=ranked.account_id);

INSERT INTO zsjos_positioning_application_log(account_id,submission_id,applied_by,expected_version,idempotency_key,tenant_id)
SELECT a.account_id,a.submission_id,NULL,0,CONCAT('legacy-positioning-',a.account_id),a.tenant_id
FROM zsjos_positioning_application a WHERE a.applied_by IS NULL
AND NOT EXISTS(SELECT 1 FROM zsjos_positioning_application_log l WHERE l.tenant_id=a.tenant_id AND l.account_id=a.account_id);

INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '应用或更换定位卡','zsjos:positioning-card:apply',3,90,parent_id,'','','',0,b'1',b'1',b'1','1','1',b'0'
FROM system_menu WHERE permission='zsjos:positioning-card:create' AND deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu existing WHERE existing.permission='zsjos:positioning-card:apply' AND existing.deleted=0)
ORDER BY id LIMIT 1;

INSERT IGNORE INTO zsjos_schema_version(version,description,checksum) VALUES('V258','课程服务定位卡与账号应用关系','positioning-service-application-v1');
