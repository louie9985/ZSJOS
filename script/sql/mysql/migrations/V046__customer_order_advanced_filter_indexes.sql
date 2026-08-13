-- V046: indexes used by the controlled customer/order advanced-filter subqueries.
-- Dependencies/order: apply after V045. The queried ZSJOS person and order tables already exist.
-- Data scope: metadata only; no customer, lead, opportunity, order, permission, or dictionary rows change.
-- Repeatability: each index is added only when its exact name is absent.
-- Rollback limitation: dropping these indexes is logically reversible but may degrade advanced-filter queries;
-- validate query plans before a controlled rollback. This file must not be executed without environment approval.

SET @ddl = (SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='zsjos_person'
    AND index_name='idx_tenant_last_seen'),
  'SELECT 1',
  'ALTER TABLE `zsjos_person` ADD KEY `idx_tenant_last_seen` (`tenant_id`,`last_seen_at`,`id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(EXISTS(
  SELECT 1 FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='zsjos_order'
    AND index_name='idx_tenant_formal_sales_status'),
  'SELECT 1',
  'ALTER TABLE `zsjos_order` ADD KEY `idx_tenant_formal_sales_status` (`tenant_id`,`formal_sales_user_id`,`status`,`id`)'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V046','Add customer and order advanced-filter indexes','customer-order-advanced-filter-indexes-v1')
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V046','Add customer and order advanced-filter indexes',
        SHA2('customer-order-advanced-filter-indexes-v1',256),'legacy',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
