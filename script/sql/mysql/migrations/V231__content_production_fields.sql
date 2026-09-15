SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'zsjos_content' AND column_name = 'purpose_value'),
    'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN purpose_value varchar(64) DEFAULT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'zsjos_content' AND column_name = 'purpose_label_snapshot'),
    'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN purpose_label_snapshot varchar(128) DEFAULT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'zsjos_content' AND column_name = 'format_value'),
    'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN format_value varchar(64) DEFAULT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'zsjos_content' AND column_name = 'format_label_snapshot'),
    'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN format_label_snapshot varchar(128) DEFAULT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'zsjos_content' AND column_name = 'detail_url'),
    'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN detail_url varchar(1000) DEFAULT NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'zsjos_content' AND column_name = 'lead_resource_url'),
    'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN lead_resource_url varchar(1024) DEFAULT NULL COMMENT ''引流资料 HTTPS 链接''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'zsjos_content' AND column_name = 'planned_publish_at'),
    'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN planned_publish_at datetime DEFAULT NULL COMMENT ''预计发布时间''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

