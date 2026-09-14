SET NAMES utf8mb4;

SET @has_revision_of_batch_id := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'zsjos_content_review_batch'
      AND column_name = 'revision_of_batch_id'
);
SET @sql := IF(@has_revision_of_batch_id = 0,
    'ALTER TABLE zsjos_content_review_batch ADD COLUMN revision_of_batch_id BIGINT NULL COMMENT ''上一审批轮次批次ID'' AFTER student_person_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_revision_index := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'zsjos_content_review_batch'
      AND index_name = 'idx_zsjos_crb_revision'
);
SET @sql := IF(@has_revision_index = 0,
    'ALTER TABLE zsjos_content_review_batch ADD KEY idx_zsjos_crb_revision (revision_of_batch_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
