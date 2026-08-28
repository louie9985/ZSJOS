-- V152: frozen direct relations between BPM process instances.
-- Dependencies/order: apply after V151. No Flowable ACT_* table is modified.
-- Data scope: creates an empty relation table and version markers; no historical rows are backfilled.
-- Repeatability: CREATE TABLE IF NOT EXISTS and idempotent version writes allow safe reruns.
-- Recovery: forward-only. Retain the table while audit/history retention is required.

DROP PROCEDURE IF EXISTS `zsjos_v152_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v152_apply`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V151')
     OR NOT EXISTS (SELECT 1 FROM `zsjos_module_schema_version`
                    WHERE `module_code`='core' AND `version`='V151') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V152 requires V151 in both schema-version registries';
  END IF;

  -- bpm_process_instance_relation
CREATE TABLE IF NOT EXISTS `bpm_process_instance_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `source_process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源流程实例编号',
  `target_process_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标流程实例编号',
  `form_field` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表单字段',
  `sort` int NOT NULL DEFAULT '0' COMMENT '冻结顺序',
  `target_name_snapshot` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标标题快照',
  `target_process_definition_id_snapshot` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标流程定义编号快照',
  `target_process_definition_name_snapshot` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标流程定义名称快照',
  `target_display_no_snapshot` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标可读流程编号快照',
  `target_business_key_snapshot` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标业务键快照',
  `target_start_user_name_snapshot` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标发起人名称快照',
  `target_start_time_snapshot` datetime DEFAULT NULL COMMENT '目标发起时间快照',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_bpm_relation_source_field_target` (`tenant_id`,`source_process_instance_id`,`form_field`,`target_process_instance_id`),
  KEY `idx_bpm_relation_source_field_sort` (`tenant_id`,`source_process_instance_id`,`form_field`,`sort`),
  KEY `idx_bpm_relation_target` (`tenant_id`,`target_process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程实例关联审批表';


  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
  VALUES ('V152','BPM process instance relations',
          SHA2('V152__bpm_process_instance_relation.sql',256),NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V152','BPM process instance relations',
          SHA2('V152__bpm_process_instance_relation.sql',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v152_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v152_apply`;
