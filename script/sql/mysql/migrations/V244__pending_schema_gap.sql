-- V244: close additive schema gaps present in the desired Core schema but absent from V185-V243.
-- UTF-8. Exact data scope: 13 nullable columns across three existing tables; no row backfill.
-- Prerequisites: V243 and the V020/V096/V187 table definitions.
-- Repeatability: every ADD COLUMN is guarded through information_schema.
-- Historical records retain NULL because no trustworthy snapshot source exists.
-- Rollback limitation: application rollback may leave the nullable columns in place; dropping them
-- later requires a separate audit because newer writes may contain snapshot or production data.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v244_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v244_apply`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_lead_intended_product' AND column_name='selected_specs_json') THEN
    ALTER TABLE `zsjos_lead_intended_product`
      ADD COLUMN `selected_specs_json` json DEFAULT NULL COMMENT 'SKU规格标签快照'
      AFTER `selected_attr_values_json`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='product_id') THEN
    ALTER TABLE `zsjos_exam_schedule`
      ADD COLUMN `product_id` bigint DEFAULT NULL COMMENT '考期产品编号，空表示分类范围' AFTER `category_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='product_name_snapshot') THEN
    ALTER TABLE `zsjos_exam_schedule`
      ADD COLUMN `product_name_snapshot` varchar(255) DEFAULT NULL COMMENT '产品名称快照' AFTER `product_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='selected_attrs_json') THEN
    ALTER TABLE `zsjos_exam_schedule`
      ADD COLUMN `selected_attrs_json` json DEFAULT NULL COMMENT '已选规格条件' AFTER `product_name_snapshot`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='selected_specs_json') THEN
    ALTER TABLE `zsjos_exam_schedule`
      ADD COLUMN `selected_specs_json` json DEFAULT NULL COMMENT '已选规格字段和值标签快照' AFTER `selected_attrs_json`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_exam_schedule' AND column_name='frozen_skus_json') THEN
    ALTER TABLE `zsjos_exam_schedule`
      ADD COLUMN `frozen_skus_json` json DEFAULT NULL COMMENT '发布时适用SKU快照' AFTER `selected_specs_json`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_production_ticket' AND column_name='purpose_value') THEN
    ALTER TABLE `zsjos_production_ticket`
      ADD COLUMN `purpose_value` varchar(64) DEFAULT NULL AFTER `script_text`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_production_ticket' AND column_name='purpose_label_snapshot') THEN
    ALTER TABLE `zsjos_production_ticket`
      ADD COLUMN `purpose_label_snapshot` varchar(128) DEFAULT NULL AFTER `purpose_value`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_production_ticket' AND column_name='format_value') THEN
    ALTER TABLE `zsjos_production_ticket`
      ADD COLUMN `format_value` varchar(64) DEFAULT NULL AFTER `purpose_label_snapshot`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_production_ticket' AND column_name='format_label_snapshot') THEN
    ALTER TABLE `zsjos_production_ticket`
      ADD COLUMN `format_label_snapshot` varchar(128) DEFAULT NULL AFTER `format_value`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_production_ticket' AND column_name='detail_url') THEN
    ALTER TABLE `zsjos_production_ticket`
      ADD COLUMN `detail_url` varchar(1000) DEFAULT NULL AFTER `format_label_snapshot`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_production_ticket' AND column_name='comment_hook') THEN
    ALTER TABLE `zsjos_production_ticket`
      ADD COLUMN `comment_hook` text DEFAULT NULL AFTER `detail_url`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_production_ticket' AND column_name='reference_content_version_id') THEN
    ALTER TABLE `zsjos_production_ticket`
      ADD COLUMN `reference_content_version_id` bigint DEFAULT NULL AFTER `comment_hook`;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v244_apply`();
DROP PROCEDURE `zsjos_v244_apply`;
