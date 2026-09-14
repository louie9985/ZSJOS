-- UTF-8. Requires V203 and the existing zsjos_media_account table.
-- Scope: additive account-creation command metadata only; no business row is deleted or rewritten.
-- Repeatable through information_schema guards. Rollback is forward-only once new commands exist.
SET NAMES utf8mb4;

SET @v204_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_media_account' AND column_name='create_service_relation_id'), 'SELECT 1',
  'ALTER TABLE zsjos_media_account ADD COLUMN create_service_relation_id bigint DEFAULT NULL AFTER rebind_result_reason');
PREPARE v204_stmt FROM @v204_sql; EXECUTE v204_stmt; DEALLOCATE PREPARE v204_stmt;
SET @v204_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_media_account' AND column_name='create_operator_user_id'), 'SELECT 1',
  'ALTER TABLE zsjos_media_account ADD COLUMN create_operator_user_id bigint DEFAULT NULL AFTER create_service_relation_id');
PREPARE v204_stmt FROM @v204_sql; EXECUTE v204_stmt; DEALLOCATE PREPARE v204_stmt;
SET @v204_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_media_account' AND column_name='create_idempotency_key'), 'SELECT 1',
  'ALTER TABLE zsjos_media_account ADD COLUMN create_idempotency_key varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL AFTER create_operator_user_id');
PREPARE v204_stmt FROM @v204_sql; EXECUTE v204_stmt; DEALLOCATE PREPARE v204_stmt;
SET @v204_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_media_account' AND column_name='create_request_fingerprint'), 'SELECT 1',
  'ALTER TABLE zsjos_media_account ADD COLUMN create_request_fingerprint char(64) DEFAULT NULL AFTER create_idempotency_key');
PREPARE v204_stmt FROM @v204_sql; EXECUTE v204_stmt; DEALLOCATE PREPARE v204_stmt;
SET @v204_sql = IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='zsjos_media_account' AND index_name='uk_tenant_account_create_key'), 'SELECT 1',
  'ALTER TABLE zsjos_media_account ADD UNIQUE KEY uk_tenant_account_create_key (tenant_id,create_idempotency_key)');
PREPARE v204_stmt FROM @v204_sql; EXECUTE v204_stmt; DEALLOCATE PREPARE v204_stmt;

INSERT INTO zsjos_schema_version(version,description,checksum,installed_at)
VALUES ('V204','authorize and deduplicate media account creation',SHA2('V204__media_account_create_command.sql',256),NOW())
ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V204','authorize and deduplicate media account creation',SHA2('V204__media_account_create_command.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
