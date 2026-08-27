-- V141: normalized Lead provider attribution and persisted media-screen snapshots.
-- Dependencies/order: apply after V140. V143 may later add provable Partner employee snapshots.
-- Data scope: additive Lead columns plus an empty media-screen snapshot table. Historical labels and
-- organization relationships are never inferred from current System or Partner state.
-- Repeatability: guarded columns/indexes and version upserts. An old non-empty v1 snapshot table blocks
-- execution; export and review those rows before any structural replacement.
-- Recovery: retain Lead attribution columns and frozen rows; restore the pre-change empty-table DDL if needed.

DROP PROCEDURE IF EXISTS `zsjos_v141_upgrade`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v141_upgrade`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                 AND table_name='zsjos_lead' AND column_name='provider_owner_type') THEN
    ALTER TABLE `zsjos_lead`
      ADD COLUMN `provider_owner_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL
        COMMENT '规范提供方类型 system_user/partner' AFTER `partner_id`,
      ADD COLUMN `provider_owner_id` bigint DEFAULT NULL COMMENT '规范提供方内部ID' AFTER `provider_owner_type`,
      ADD COLUMN `provider_owner_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
        COMMENT '首次计数时提供方名称快照' AFTER `provider_owner_id`,
      ADD COLUMN `contribution_user_id_snapshot` bigint DEFAULT NULL
        COMMENT '首次计数时业绩员工ID快照' AFTER `provider_owner_name_snapshot`,
      ADD COLUMN `contribution_user_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
        COMMENT '首次计数时业绩员工姓名快照' AFTER `contribution_user_id_snapshot`,
      ADD COLUMN `contribution_supervisor_user_id_snapshot` bigint DEFAULT NULL
        COMMENT '首次计数时直属主管ID快照' AFTER `contribution_user_name_snapshot`,
      ADD COLUMN `contribution_supervisor_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
        COMMENT '首次计数时直属主管姓名快照' AFTER `contribution_supervisor_user_id_snapshot`,
      ADD COLUMN `contribution_dept_id_snapshot` bigint DEFAULT NULL
        COMMENT '首次计数时业绩部门ID快照' AFTER `contribution_supervisor_name_snapshot`,
      ADD COLUMN `contribution_dept_name_snapshot` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
        COMMENT '首次计数时业绩部门名称快照' AFTER `contribution_dept_id_snapshot`,
      ADD COLUMN `counted_at` datetime DEFAULT NULL COMMENT '首次计入客资业绩时间' AFTER `submitted_at`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                 AND table_name='zsjos_lead' AND index_name='idx_tenant_provider_owner') THEN
    ALTER TABLE `zsjos_lead` ADD KEY `idx_tenant_provider_owner`
      (`tenant_id`,`provider_owner_type`,`provider_owner_id`,`counted_at`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
                 AND table_name='zsjos_lead' AND index_name='idx_tenant_contribution') THEN
    ALTER TABLE `zsjos_lead` ADD KEY `idx_tenant_contribution`
      (`tenant_id`,`contribution_dept_id_snapshot`,`contribution_user_id_snapshot`,`counted_at`);
  END IF;

  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
             AND table_name='zsjos_media_screen_daily_snapshot')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
                     AND table_name='zsjos_media_screen_daily_snapshot' AND column_name='contribution_type') THEN
    IF (SELECT COUNT(*) FROM `zsjos_media_screen_daily_snapshot`) > 0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V141 blocked: legacy media-screen snapshots are not empty';
    END IF;
    DROP TABLE `zsjos_media_screen_daily_snapshot`;
  END IF;
END$$
DELIMITER ;
CALL `zsjos_v141_upgrade`();
DROP PROCEDURE `zsjos_v141_upgrade`;

-- Backfill only stable facts already persisted on each Lead.
UPDATE `zsjos_lead`
SET `updater`=CASE WHEN `counted_at` IS NULL THEN 'migration-V141' ELSE `updater` END,
    `update_time`=CASE WHEN `counted_at` IS NULL THEN NOW() ELSE `update_time` END,
    `counted_at`=COALESCE(`counted_at`,`submitted_at`),
    `provider_owner_type`=COALESCE(`provider_owner_type`,
      CASE WHEN `source_type`='partner' AND `partner_id` IS NOT NULL THEN 'partner'
           WHEN `source_type`='internal_new_media' AND `source_user_id` IS NOT NULL THEN 'system_user'
           WHEN `source_type`='sales_self_sourced' AND `source_provider_user_id` IS NOT NULL THEN 'system_user'
           ELSE NULL END),
    `provider_owner_id`=COALESCE(`provider_owner_id`,
      CASE WHEN `source_type`='partner' THEN `partner_id`
           WHEN `source_type`='internal_new_media' THEN `source_user_id`
           WHEN `source_type`='sales_self_sourced' THEN `source_provider_user_id`
           ELSE NULL END),
    `contribution_user_id_snapshot`=COALESCE(`contribution_user_id_snapshot`,
      CASE WHEN `source_type`='internal_new_media' THEN `source_user_id`
           WHEN `source_type`='sales_self_sourced' AND `source_provider_user_id` IS NOT NULL
             THEN `source_provider_user_id`
           ELSE NULL END),
    `contribution_dept_id_snapshot`=COALESCE(`contribution_dept_id_snapshot`,
      CASE WHEN `source_type` IN ('internal_new_media','sales_self_sourced')
             AND (`source_type`='internal_new_media' OR `source_provider_user_id` IS NOT NULL)
           THEN `source_dept_id` ELSE NULL END)
WHERE `deleted`=b'0' AND (`counted_at` IS NULL OR `provider_owner_type` IS NULL
  OR `provider_owner_id` IS NULL OR `contribution_user_id_snapshot` IS NULL
  OR `contribution_dept_id_snapshot` IS NULL);

CREATE TABLE IF NOT EXISTS `zsjos_media_screen_daily_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_date` date NOT NULL,
  `contribution_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'direct/part_time',
  `department_id` bigint NOT NULL,
  `department_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `supervisor_id` bigint DEFAULT NULL,
  `supervisor_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_enabled` bit(1) NOT NULL DEFAULT b'1',
  `today_count` int NOT NULL DEFAULT 0,
  `week_count` int NOT NULL DEFAULT 0,
  `month_total` int NOT NULL DEFAULT 0,
  `month_effective` int NOT NULL DEFAULT 0,
  `partner_details_json` json DEFAULT NULL COMMENT '兼职明细冻结快照',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_snapshot_contributor`
    (`tenant_id`,`snapshot_date`,`contribution_type`,`department_id`,`member_id`),
  KEY `idx_tenant_snapshot` (`tenant_id`,`snapshot_date`,`contribution_type`),
  KEY `idx_tenant_snapshot_supervisor` (`tenant_id`,`snapshot_date`,`supervisor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='新媒体客资大屏累计冻结快照';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V141','normalized Lead provider attribution and media-screen snapshots',
        'V141__media_screen_daily_snapshot.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V141','normalized Lead provider attribution and media-screen snapshots',
        SHA2('V141__media_screen_daily_snapshot.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
