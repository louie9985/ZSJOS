-- Makes intended-product uniqueness compatible with logical deletion.
-- Dependencies: V020, zsjos_lead_intended_product, and zsjos_schema_version.
-- Data scope: index metadata and one generated column only; no business rows are inserted, deleted, or rewritten.
-- Execution order: add the generated column, replace the unique index, then record V021.
-- Repeatability: every DDL operation is guarded by information_schema checks and version inserts use INSERT IGNORE.
-- Rollback limitation: restoring the old index would make repeated edits fail again while deleted rows retain product keys.

SET @ddl = (SELECT IF(
  NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='zsjos_lead_intended_product'
      AND column_name='active_product_ref'
  ),
  'ALTER TABLE `zsjos_lead_intended_product` ADD COLUMN `active_product_ref` varchar(128) GENERATED ALWAYS AS (IF(`deleted` = 0, `product_ref`, NULL)) STORED AFTER `category_path_snapshot`',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='zsjos_lead_intended_product'
      AND index_name='uk_tenant_lead_product'
  ),
  'ALTER TABLE `zsjos_lead_intended_product` DROP INDEX `uk_tenant_lead_product`',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
  NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='zsjos_lead_intended_product'
      AND index_name='uk_tenant_lead_active_product'
  ),
  'ALTER TABLE `zsjos_lead_intended_product` ADD UNIQUE KEY `uk_tenant_lead_active_product` (`tenant_id`,`lead_id`,`active_product_ref`)',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V021','Make lead intended-product uniqueness active-row only','lead-intended-product-active-unique-key-v1');

INSERT IGNORE INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V021','Make lead intended-product uniqueness active-row only',
        SHA2('lead-intended-product-active-unique-key-v1', 256),'legacy',NOW());
