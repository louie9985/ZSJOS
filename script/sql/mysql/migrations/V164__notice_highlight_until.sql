-- V164: configurable employee notice highlight reminder deadline.
-- Dependencies: V163 and the System system_notice table with V148 lifecycle columns.
-- Repeatable and non-destructive: adds one nullable column; existing notices remain ordinary notices.
-- Rollback limitation: dropping the column would discard configured deadlines and requires a reviewed forward migration.
SET NAMES utf8mb4;

SET @v164_sql = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'system_notice'
    AND column_name = 'highlight_until') = 0,
  'ALTER TABLE system_notice ADD COLUMN highlight_until datetime DEFAULT NULL COMMENT ''高亮提醒截止时间'' AFTER offline_time',
  'SELECT 1');
PREPARE v164_stmt FROM @v164_sql;
EXECUTE v164_stmt;
DEALLOCATE PREPARE v164_stmt;

INSERT INTO zsjos_schema_version (version, description, checksum, installed_at)
SELECT 'V164', 'Notice highlight reminder deadline', SHA2('V164__notice_highlight_until.sql', 256), NOW()
WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version = 'V164');
INSERT INTO zsjos_module_schema_version (module_code, version, description, checksum, release_version, installed_at)
SELECT 'core', 'V164', 'Notice highlight reminder deadline', SHA2('V164__notice_highlight_until.sql', 256), 'baseline', NOW()
WHERE NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code = 'core' AND version = 'V164');
