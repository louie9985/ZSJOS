-- V141: persisted daily snapshots for the new-media contribution screen.
-- Dependencies/order: apply after V140. The table is empty on creation; no historical labels are invented.
-- Repeatability: CREATE TABLE IF NOT EXISTS and version upserts make reruns safe.
-- Rollback limitation: forward-only once snapshots exist; export and retain frozen history before any reviewed removal.

CREATE TABLE IF NOT EXISTS `zsjos_media_screen_daily_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_date` date NOT NULL,
  `supervisor_id` bigint DEFAULT NULL,
  `department_name` varchar(100) NOT NULL,
  `member_id` bigint NOT NULL,
  `member_name` varchar(100) NOT NULL,
  `submitted_count` int NOT NULL DEFAULT 0,
  `valid_count` int NOT NULL DEFAULT 0,
  `part_time_submitted_count` int NOT NULL DEFAULT 0,
  `part_time_valid_count` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_snapshot_member` (`tenant_id`,`snapshot_date`,`member_id`),
  KEY `idx_tenant_snapshot` (`tenant_id`,`snapshot_date`),
  KEY `idx_tenant_snapshot_supervisor` (`tenant_id`,`snapshot_date`,`supervisor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新媒体客资大屏每日冻结快照';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V141','media screen daily snapshot','V141__media_screen_daily_snapshot.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V141','media screen daily snapshot',
        SHA2('V141__media_screen_daily_snapshot.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
