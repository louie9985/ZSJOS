-- V105: align the production-ticket persistence contract with the current form.
-- Scope: one nullable scheduling column; no business rows are deleted or rewritten.
-- Repeatability: guarded by information_schema metadata and a schema-version marker.
-- Recovery: forward-only schema change; restore NOT NULL only after backfilling every NULL value.
SET NAMES utf8mb4;

SET @column_nullable := (
  SELECT IS_NULLABLE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zsjos_production_ticket'
    AND COLUMN_NAME = 'expected_delivered_at'
);
SET @ddl := IF(@column_nullable = 'NO',
  'ALTER TABLE zsjos_production_ticket MODIFY COLUMN expected_delivered_at datetime NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V105','Align production-ticket delivery time with current form','production-ticket-delivery-time-v1')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);

INSERT INTO zsjos_module_schema_version
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V105','Align production-ticket delivery time with current form',
        SHA2('production-ticket-delivery-time-v1',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
