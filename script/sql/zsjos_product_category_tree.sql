-- ZSJOS 任意层级课程分类兼容增量脚本。
-- 依赖：zsjos_product_hierarchy.sql、zsjos_product_spu_sku.sql 已执行。
-- 影响：只更新字段注释并增加通用 Lead 分类快照列；不修改分类 ID、层级数据、SPU/SKU 或历史快照。
-- 可重复执行；本文件只提供迁移，不由应用自动执行。执行前应备份相关三张业务表并先在测试库验证。

ALTER TABLE `zsjos_product_category`
  MODIFY COLUMN `level` tinyint NOT NULL COMMENT '分类树深度（1-10，由服务端维护）';

ALTER TABLE `zsjos_product`
  MODIFY COLUMN `category_id` bigint DEFAULT NULL COMMENT '所属叶子分类编号';

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT ''提交时叶子分类编号''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'category_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `category_name_snapshot` varchar(100) DEFAULT NULL COMMENT ''提交时叶子分类名称快照''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'category_name_snapshot');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `category_path_snapshot` json DEFAULT NULL COMMENT ''提交时完整分类路径快照''',
  'SELECT 1') FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_intended_product' AND column_name = 'category_path_snapshot');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
