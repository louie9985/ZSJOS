-- V005: normalized responsibility/commitment fields and administrative history.
-- Depends on V004. Repeatable and non-destructive; does not import workbook rows.
DROP PROCEDURE IF EXISTS `eam_v005_apply`;
DELIMITER $$
CREATE PROCEDURE `eam_v005_apply`()
BEGIN
  DECLARE lock_ok INT DEFAULT 0;
  SELECT GET_LOCK('eam:migration:V005', 30) INTO lock_ok;
  IF lock_ok <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'EAM V005 lock unavailable'; END IF;
  START TRANSACTION;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='use_user_name_snapshot') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `use_user_name_snapshot` varchar(100) DEFAULT NULL COMMENT '使用人姓名快照' AFTER `use_employee_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='supervisor_user_id') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `supervisor_user_id` bigint DEFAULT NULL COMMENT '直属上级用户编号' AFTER `use_user_name_snapshot`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='supervisor_name_snapshot') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `supervisor_name_snapshot` varchar(100) DEFAULT NULL COMMENT '直属上级姓名快照' AFTER `supervisor_user_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='join_date') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `join_date` date DEFAULT NULL COMMENT '使用人入司日期' AFTER `supervisor_name_snapshot`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='commitment_accepted') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `commitment_accepted` bit(1) DEFAULT NULL COMMENT '使用人承诺是否确认' AFTER `join_date`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='eam_asset' AND column_name='commitment_date') THEN
    ALTER TABLE `eam_asset` ADD COLUMN `commitment_date` date DEFAULT NULL COMMENT '承诺日期' AFTER `commitment_accepted`;
  END IF;
  CREATE TABLE IF NOT EXISTS `eam_asset_verification` (
    `id` bigint NOT NULL AUTO_INCREMENT, `asset_id` bigint NOT NULL, `result` varchar(100) DEFAULT NULL COMMENT '核对结果',
    `label_status` varchar(20) DEFAULT NULL COMMENT '标签状态', `verifier_user_id` bigint DEFAULT NULL,
    `verifier_name_snapshot` varchar(100) DEFAULT NULL, `verified_at` datetime DEFAULT NULL, `remark` varchar(500) DEFAULT NULL,
    `import_batch_id` bigint DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0, PRIMARY KEY (`id`),
    KEY `idx_eam_asset_verification_asset` (`tenant_id`,`asset_id`,`verified_at`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产行政核对历史';
  CREATE TABLE IF NOT EXISTS `eam_asset_handover` (
    `id` bigint NOT NULL AUTO_INCREMENT, `asset_id` bigint NOT NULL, `content` varchar(500) DEFAULT NULL COMMENT '交接内容',
    `from_user_id` bigint DEFAULT NULL, `to_user_id` bigint DEFAULT NULL, `handover_time` datetime DEFAULT NULL,
    `remark` varchar(500) DEFAULT NULL, `import_batch_id` bigint DEFAULT NULL, `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0, PRIMARY KEY (`id`), KEY `idx_eam_asset_handover_asset` (`tenant_id`,`asset_id`,`handover_time`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EAM 资产交接历史';
  COMMIT;
  DO RELEASE_LOCK('eam:migration:V005');
END$$
DELIMITER ;
CALL `eam_v005_apply`();
DROP PROCEDURE IF EXISTS `eam_v005_apply`;
