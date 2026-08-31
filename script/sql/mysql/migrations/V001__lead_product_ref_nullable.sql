-- Existing environments: allow an intention with an unknown SPU to persist.
CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

SET @ddl = (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_lead_intended_product' AND column_name='product_ref' AND is_nullable='NO'),
  'ALTER TABLE `zsjos_lead_intended_product` MODIFY COLUMN `product_ref` varchar(128) NULL COMMENT ''兼容 SPU 稳定引用，未明确课程时为空''',
  'SELECT 1'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V001', 'Allow unknown SPU intention product_ref to be NULL', 'lead-product-ref-nullable-v1');
