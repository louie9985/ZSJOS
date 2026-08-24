-- V125: add tenant-daily Person business-number allocation for newly created students.
-- Existing zsjos_person.person_no values are durable and intentionally not rewritten.
-- Repeatability: guarded DDL and version upserts; no Person or relationship rows are changed.
-- Rollback limitation: allocated student numbers are durable business identifiers.

CREATE TABLE IF NOT EXISTS `zsjos_person_no_daily_counter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `sequence_date` date NOT NULL COMMENT '北京时间业务日期',
  `current_value` int NOT NULL COMMENT '当日最后已分配循环序号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_sequence_date` (`tenant_id`,`sequence_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZSJOS 学员业务编号日序';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V125','Tenant-daily student business numbers',
        'V125__student_business_number.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V125','Tenant-daily student business numbers',
        SHA2('V125__student_business_number.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
