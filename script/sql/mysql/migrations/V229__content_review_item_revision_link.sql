SET NAMES utf8mb4;
SET @has_previous_item := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'zsjos_content_review_batch_item'
    AND column_name = 'previous_item_id'
);
SET @ddl := IF(@has_previous_item = 0,
  'ALTER TABLE zsjos_content_review_batch_item ADD COLUMN previous_item_id BIGINT NULL COMMENT ''上一审批轮次对应条目''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @has_previous_index := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'zsjos_content_review_batch_item'
    AND index_name = 'idx_content_review_item_previous'
);
SET @ddl := IF(@has_previous_index = 0,
  'CREATE INDEX idx_content_review_item_previous ON zsjos_content_review_batch_item(tenant_id, previous_item_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
