-- V245: add the two content snapshot/reference columns omitted by V231.
-- UTF-8. Exact data scope: two nullable columns on zsjos_content; no backfill.
-- Repeatability: each column is guarded through information_schema.
-- Historical rows remain NULL because no trustworthy source exists for reconstruction.
SET NAMES utf8mb4;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_content' AND column_name='comment_hook'), 'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN comment_hook text DEFAULT NULL AFTER detail_url');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_content' AND column_name='reference_content_version_id'), 'SELECT 1', 'ALTER TABLE zsjos_content ADD COLUMN reference_content_version_id bigint DEFAULT NULL AFTER comment_hook');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
