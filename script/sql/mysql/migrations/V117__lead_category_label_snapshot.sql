-- Persist the administrator-owned Lead category label at business selection time.
-- Dependencies: the existing zsjos_lead and zsjos_lead_duplicate_review tables.
-- Data scope: adds nullable snapshot columns only; historical Leads and reviews are not backfilled.
-- Repeatability: each additive column is guarded by information_schema.
-- Recovery: forward-only; unused nullable columns may remain if application rollout is reverted.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v117_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v117_apply`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema=DATABASE() AND table_name='zsjos_lead'
                   AND column_name='lead_category_label_snapshot') THEN
    ALTER TABLE `zsjos_lead`
      ADD COLUMN `lead_category_label_snapshot` varchar(128) DEFAULT NULL
      COMMENT '客资分类选择时标签快照' AFTER `lead_category`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema=DATABASE() AND table_name='zsjos_lead_duplicate_review'
                   AND column_name='lead_category_label_snapshot') THEN
    ALTER TABLE `zsjos_lead_duplicate_review`
      ADD COLUMN `lead_category_label_snapshot` varchar(128) DEFAULT NULL
      COMMENT '提交时客资分类标签快照' AFTER `submission_snapshot`;
  END IF;

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
  VALUES ('V117','Lead category label snapshot','lead-category-label-snapshot-v1')
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

  INSERT INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V117','Lead category label snapshot',
          SHA2('lead-category-label-snapshot-v1',256),'baseline',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;
CALL `zsjos_v117_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v117_apply`;
