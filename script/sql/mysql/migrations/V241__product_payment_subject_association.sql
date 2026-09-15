-- =============================================
-- V241: 产品支付主体关联表与批量配置功能
-- 每个租户内每个产品关联一个支付主体，支持批量配置
-- Depends on: V239 (payment subject table)
-- Author: Claude
-- Date: 2026-09-15
-- =============================================

-- 开发基线修正：V239 后执行，仅转换无业务记录的旧表；新表和已修正表可重复执行。
-- 非空旧表拒绝转换，须另行审核编码与 ID 映射。客户端不得使用 --force。
-- 不删除业务行；DDL 隐式提交，回滚仅可在表仍为空时恢复备份结构。
SET NAMES utf8mb4;

-- 1. 创建产品支付主体关联表
CREATE TABLE IF NOT EXISTS `zsjos_product_payment_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联编号',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `payment_subject_id` bigint NOT NULL COMMENT '支付主体ID',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_product_subject` (`tenant_id`,`product_id`,`deleted`),
  KEY `idx_tenant_subject` (`tenant_id`,`payment_subject_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品支付主体关联';

-- CREATE IF NOT EXISTS 不会升级旧表；空表才允许改变关联键语义。
SET @payment_subject_legacy = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'zsjos_product_payment_subject'
      AND column_name = 'subject_code'
) = 1 AND (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'zsjos_product_payment_subject'
      AND column_name = 'payment_subject_id'
) = 0;
CREATE TEMPORARY TABLE payment_subject_empty_guard (
    row_count bigint NOT NULL CHECK (row_count = 0)
);
INSERT INTO payment_subject_empty_guard
SELECT COUNT(*) FROM zsjos_product_payment_subject WHERE @payment_subject_legacy;
DROP TEMPORARY TABLE payment_subject_empty_guard;
SET @payment_subject_ddl = IF(@payment_subject_legacy,
    'ALTER TABLE zsjos_product_payment_subject CHANGE COLUMN subject_code payment_subject_id bigint NOT NULL COMMENT ''支付主体ID''',
    'SELECT 1');
PREPARE payment_subject_statement FROM @payment_subject_ddl;
EXECUTE payment_subject_statement;
DEALLOCATE PREPARE payment_subject_statement;

-- 2. 菜单创建移至 V242（在正确的父菜单下）
-- 权限授权移至 V243

-- 3. 更新版本记录
INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
VALUES ('V241', '产品支付主体关联表', SHA2('V241__product_payment_subject_association.sql', 256), NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);

INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
VALUES ('payment', 'V241', '产品支付主体关联', SHA2('V241__product_payment_subject_association.sql', 256), 'v1.0', NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum), release_version=VALUES(release_version);
