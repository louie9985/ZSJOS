-- UTF-8. V271: media account deletion approval. Additive, repeatable, no role grants.
-- Development correction: MySQL 8 uses information_schema guards, not ADD COLUMN IF NOT EXISTS.
-- Prerequisites: V270, existing media-account table/page and both version ledgers; run before V272.
-- Partial failures can be replayed: each missing column/index is added; existing values are retained.
-- Use a UTF-8 client with stop-on-error; a version record alone does not prove completion.
-- Rollback: retain additive schema and snapshots; dropping columns/tables would lose history.
-- Deployed file-byte checksum changes require a separately reviewed rollout, not auto-reconciliation.
-- Scope: account workflow state and immutable request snapshot; historical business rows remain.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS zsjos_media_account_delete_request (
 id bigint NOT NULL AUTO_INCREMENT, account_id bigint NOT NULL, process_instance_id varchar(64) NOT NULL,
 account_snapshot_json json NOT NULL, reason varchar(500) NOT NULL, requested_by_user_id bigint NOT NULL,
 reviewer_user_id bigint NOT NULL, status varchar(32) NOT NULL, result_reason varchar(500) DEFAULT NULL,
 attempt_count int NOT NULL DEFAULT 0, last_error varchar(1000) DEFAULT NULL, version int NOT NULL DEFAULT 0,
 creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL, PRIMARY KEY(id),
 UNIQUE KEY uk_tenant_process (tenant_id,process_instance_id,deleted), KEY idx_tenant_account (tenant_id,account_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体账号删除审批快照';
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='delete_process_instance_id'),
  'SELECT 1', 'ALTER TABLE `zsjos_media_account` ADD COLUMN `delete_process_instance_id` varchar(64) DEFAULT NULL'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='delete_status'),
  'SELECT 1', 'ALTER TABLE `zsjos_media_account` ADD COLUMN `delete_status` varchar(24) DEFAULT NULL'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='delete_requested_by_user_id'),
  'SELECT 1', 'ALTER TABLE `zsjos_media_account` ADD COLUMN `delete_requested_by_user_id` bigint DEFAULT NULL'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='delete_reviewer_user_id'),
  'SELECT 1', 'ALTER TABLE `zsjos_media_account` ADD COLUMN `delete_reviewer_user_id` bigint DEFAULT NULL'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='delete_reason'),
  'SELECT 1', 'ALTER TABLE `zsjos_media_account` ADD COLUMN `delete_reason` varchar(500) DEFAULT NULL'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name='delete_result_reason'),
  'SELECT 1', 'ALTER TABLE `zsjos_media_account` ADD COLUMN `delete_result_reason` varchar(500) DEFAULT NULL'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND index_name='idx_media_account_delete_process');
SET @idx_sql := IF(@idx_exists=0, 'ALTER TABLE zsjos_media_account ADD KEY idx_media_account_delete_process (delete_process_instance_id)', 'SELECT 1');
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,create_time,update_time,deleted)
SELECT '删除账号','zsjos:media-account:delete',3,9,p.id,'','','',NULL,0,b'1',b'1',b'0','V271','V271',NOW(),NOW(),b'0'
FROM system_menu p WHERE p.permission='zsjos:media-account:query' AND p.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='zsjos:media-account:delete' AND m.deleted=b'0');
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,create_time,update_time,deleted)
SELECT '审批账号删除','zsjos:media-account:delete-approve',3,10,p.id,'','','',NULL,0,b'1',b'1',b'0','V271','V271',NOW(),NOW(),b'0'
FROM system_menu p WHERE p.permission='zsjos:media-account:query' AND p.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='zsjos:media-account:delete-approve' AND m.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V271','Media account deletion approval',SHA2('V271__media_account_delete_approval.sql',256))
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES ('core','V271','Media account deletion approval',SHA2('V271__media_account_delete_approval.sql',256),'baseline')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
