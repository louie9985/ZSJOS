-- =============================================
-- V239: 支付主体配置功能
-- 支持多个通联支付主体配置，产品可关联不同主体收款
-- Author: Claude
-- Date: 2026-09-15
-- =============================================

-- 1. 创建支付主体配置表
CREATE TABLE IF NOT EXISTS `zsjos_payment_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主体编号',
  `subject_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体编码（稳定引用，如：zsj_health, zsj_school）',
  `subject_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主体名称（公司全称）',
  `cusid` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通联商户号',
  `appid` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通联应用ID',
  `orgid` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '通联机构号',
  `merchant_private_key` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商户RSA私钥（加密存储）',
  `platform_public_key` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通联平台RSA公钥',
  `rsa_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT 'RSA2' COMMENT 'RSA签名类型（RSA/RSA2）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0启用 1停用）',
  `is_default` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认主体',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示排序',
  `remark` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_subject_code` (`tenant_id`,`subject_code`,`deleted`),
  KEY `idx_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通联支付主体配置';

-- 2. 产品表不再直接关联支付主体
-- 改用 V241 创建的关联表 zsjos_product_payment_subject
-- 此处保留注释以说明设计变更

-- 3. 支付订单表增加快照字段
SET @dbname = DATABASE();
SET @tablename = 'zsjos_payment_order';
SET @columnname = 'subject_snapshot_json';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
  'SELECT 1',
  'ALTER TABLE zsjos_payment_order ADD COLUMN subject_snapshot_json json DEFAULT NULL COMMENT ''支付主体配置快照（用于退款）'' AFTER channel'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 4. 菜单创建移至 V242（在正确的父菜单下）
-- 权限授权移至 V243

-- 5. 更新版本记录
INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
VALUES ('V239', '支付主体配置功能', SHA2('V239__payment_subject_config.sql', 256), NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);

INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
VALUES ('payment', 'V239', '支付主体配置', SHA2('V239__payment_subject_config.sql', 256), 'v1.0', NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum), release_version=VALUES(release_version);
