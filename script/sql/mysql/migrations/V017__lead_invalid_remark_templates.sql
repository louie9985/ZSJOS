-- Adds an administrator-maintained dictionary for lead-invalid quick remark templates.
-- Dependencies: System dictionary tables and zsjos_schema_version.
-- Data scope: one empty dictionary type; no dictionary options or business rows are inserted.
-- Repeatability: guarded inserts preserve administrator changes on rerun.
-- Rollback limitation: disable the dictionary type if rollback is required; do not delete used configuration.

CREATE TABLE IF NOT EXISTS `zsjos_schema_version` (
  `version` varchar(64) NOT NULL,
  `description` varchar(255) NOT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';

INSERT INTO `system_dict_type`
  (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '客资无效快捷备注','zsjos_lead_invalid_remark_template',0,
       '管理员维护；初始化不提供业务选项','migration-V017',NOW(),'migration-V017',NOW(),b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_dict_type`
  WHERE `type`='zsjos_lead_invalid_remark_template' AND `deleted`=b'0'
);

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V017','Add lead-invalid quick remark template dictionary','lead-invalid-remark-template-v1');
