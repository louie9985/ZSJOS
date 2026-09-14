-- V213 additive execution audit identity metadata.
-- Scope: existing zsjos_business_audit_log after the core baseline; no business rows changed.
-- Apply in numbered migration order before audit code requiring these columns.
-- Repeatable for both upgraded and fresh-baseline schemas; index creation is guarded.
-- DDL commits implicitly. Rollback by application rollback; do not drop populated audit columns.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v213_add;
DELIMITER $$
CREATE PROCEDURE zsjos_v213_add(IN n VARCHAR(64), IN d TEXT)
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_business_audit_log' AND column_name=n) THEN
  SET @s=CONCAT('ALTER TABLE zsjos_business_audit_log ADD COLUMN ',d); PREPARE x FROM @s; EXECUTE x; DEALLOCATE PREPARE x;
 END IF;
END$$
DELIMITER ;
CALL zsjos_v213_add('initiator_user_id','initiator_user_id BIGINT DEFAULT NULL AFTER operator_name_snapshot');
CALL zsjos_v213_add('initiator_name_snapshot','initiator_name_snapshot VARCHAR(100) DEFAULT NULL AFTER initiator_user_id');
CALL zsjos_v213_add('executor_type','executor_type VARCHAR(32) DEFAULT NULL AFTER initiator_name_snapshot');
CALL zsjos_v213_add('executor_identity','executor_identity VARCHAR(100) DEFAULT NULL AFTER executor_type');
CALL zsjos_v213_add('parent_audit_id','parent_audit_id BIGINT DEFAULT NULL AFTER executor_identity');
CALL zsjos_v213_add('execution_key','execution_key VARCHAR(128) DEFAULT NULL AFTER parent_audit_id');
DROP PROCEDURE zsjos_v213_add;
SET @v213_index_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='zsjos_business_audit_log'
           AND index_name='idx_execution_key'),
 'SELECT 1',
 'ALTER TABLE zsjos_business_audit_log ADD INDEX idx_execution_key (tenant_id, execution_key)');
PREPARE v213_index_stmt FROM @v213_index_sql;
EXECUTE v213_index_stmt;
DEALLOCATE PREPARE v213_index_stmt;
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V213','Audit execution identity metadata','V213__audit_execution_identity.sql',NOW()) ON DUPLICATE KEY UPDATE description=VALUES(description);
