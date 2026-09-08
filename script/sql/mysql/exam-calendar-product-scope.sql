-- UTF-8. Development bootstrap correction: exam product scope and Lead spec labels.
-- Prerequisites: current core bootstrap through V188, including the two target tables.
-- Scope: nullable columns only on zsjos_exam_schedule and zsjos_lead_intended_product.
-- No business rows, menus, roles or account permissions are inserted, deleted or updated.
-- Repeatable: each column is guarded by information_schema. Existing history stays NULL.
-- DDL commits implicitly. Recovery: retain additive columns and roll application code back;
-- dropping columns would destroy new snapshots and is not an approved rollback.
-- Deployed upgrade environments require a separately versioned wrapper once deployment scope is confirmed.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_exam_product_scope_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_exam_product_scope_apply()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V188')
     OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V188') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Exam product scope requires core V188';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_exam_schedule' AND column_name='product_id') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN `product_id` bigint DEFAULT NULL COMMENT '考期产品编号，空表示分类范围';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_exam_schedule' AND column_name='product_name_snapshot') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN `product_name_snapshot` varchar(255) DEFAULT NULL COMMENT '产品名称快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_exam_schedule' AND column_name='selected_attrs_json') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN `selected_attrs_json` json DEFAULT NULL COMMENT '已选规格条件';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_exam_schedule' AND column_name='selected_specs_json') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN `selected_specs_json` json DEFAULT NULL COMMENT '已选规格字段和值标签快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_exam_schedule' AND column_name='frozen_skus_json') THEN
    ALTER TABLE zsjos_exam_schedule ADD COLUMN `frozen_skus_json` json DEFAULT NULL COMMENT '发布时适用SKU快照';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_lead_intended_product' AND column_name='selected_specs_json') THEN
    ALTER TABLE zsjos_lead_intended_product ADD COLUMN selected_specs_json json DEFAULT NULL COMMENT 'SKU规格标签快照';
  END IF;
END$$
DELIMITER ;
CALL zsjos_exam_product_scope_apply();
DROP PROCEDURE IF EXISTS zsjos_exam_product_scope_apply;
