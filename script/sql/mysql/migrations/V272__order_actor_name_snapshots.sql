-- UTF-8; applies after V271 with the existing order supervisor-confirmation table.
-- Scope: two nullable historical name columns only. No business-row/permission writes.
-- Repeatable through information_schema guards. Old rows intentionally remain NULL.
-- Rollback: old applications may ignore columns; dropping them loses new history and is not supported.
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_supervisor_confirmation' AND column_name='requester_name_snapshot'),'SELECT 1','ALTER TABLE `zsjos_order_supervisor_confirmation` ADD COLUMN `requester_name_snapshot` varchar(255) DEFAULT NULL COMMENT ''申请时姓名快照'''));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_supervisor_confirmation' AND column_name='supervisor_name_snapshot'),'SELECT 1','ALTER TABLE `zsjos_order_supervisor_confirmation` ADD COLUMN `supervisor_name_snapshot` varchar(255) DEFAULT NULL COMMENT ''指派时主管姓名快照'''));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V272','Order actor name snapshots',SHA2('V272__order_actor_name_snapshots.sql',256)) ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V272','Order actor name snapshots',SHA2('V272__order_actor_name_snapshots.sql',256),'baseline') ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
