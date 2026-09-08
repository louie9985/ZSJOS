-- UTF-8. V190: delivery class product/SKU snapshots.
-- Dependencies: V188 delivery class table and V187/V189 exam product scope columns.
-- Scope: additive nullable JSON snapshots on zsjos_delivery_class; no business rows are changed.
-- Repeatability: guarded information_schema ALTER statements. Rollback is forward-only; retain snapshots.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v190_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v190_apply()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V188') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V190 requires V188';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='product_id') THEN
    ALTER TABLE zsjos_delivery_class ADD COLUMN product_id bigint DEFAULT NULL COMMENT '班级产品编号' AFTER system_class;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='product_name_snapshot') THEN
    ALTER TABLE zsjos_delivery_class ADD COLUMN product_name_snapshot varchar(255) DEFAULT NULL COMMENT '班级产品名称快照' AFTER product_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='selected_attrs_json') THEN
    ALTER TABLE zsjos_delivery_class ADD COLUMN selected_attrs_json json DEFAULT NULL COMMENT '班级规格条件快照' AFTER product_name_snapshot;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='selected_specs_json') THEN
    ALTER TABLE zsjos_delivery_class ADD COLUMN selected_specs_json json DEFAULT NULL COMMENT '班级规格标签快照' AFTER selected_attrs_json;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='selected_skus_json') THEN
    ALTER TABLE zsjos_delivery_class ADD COLUMN selected_skus_json json DEFAULT NULL COMMENT '班级适用 SKU 快照' AFTER selected_specs_json;
  END IF;
  INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
  VALUES ('V190','Delivery class product scope',SHA2('V190__delivery_class_product_scope.sql',256),NOW())
  ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
  INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
  VALUES ('core','V190','Delivery class product scope',SHA2('V190__delivery_class_product_scope.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
END$$
DELIMITER ;
CALL zsjos_v190_apply();
DROP PROCEDURE IF EXISTS zsjos_v190_apply;
