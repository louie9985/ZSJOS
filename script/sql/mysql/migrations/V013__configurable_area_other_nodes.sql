-- Adds database-managed OTHER choices and direct-select province leaves.
-- Dependencies: V012 system_area and zsjos_schema_version must exist.
-- Execution order: add columns/index, backfill official selection codes, mark direct leaves,
-- insert stable special nodes, initialize sibling pinyin order, then record V013.
-- Repeatability: conditional DDL and INSERT IGNORE preserve later administrator changes.
-- Data scope: 34 special area nodes, the Hong Kong and Macao direct-select flags, and the initial
-- sibling sort values. Ordinary nodes sort by Chinese pinyin and OTHER stays last; no rows are deleted.
-- Recovery: forward-only; disable special nodes and direct-select flags instead of deleting referenced codes.

DROP PROCEDURE IF EXISTS `zsjos_add_area_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `zsjos_add_area_column_if_missing`(IN column_name_value varchar(64), IN ddl_value text)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema=DATABASE() AND table_name='system_area' AND column_name=column_name_value) THEN
    SET @ddl = ddl_value;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_add_area_column_if_missing`('selection_code',
  'ALTER TABLE `system_area` ADD COLUMN `selection_code` varchar(32) DEFAULT NULL COMMENT ''业务提交编码，特殊节点使用 OTHER；普通节点为空时回退行政区编码'' AFTER `name`');
CALL `zsjos_add_area_column_if_missing`('leaf_selectable',
  'ALTER TABLE `system_area` ADD COLUMN `leaf_selectable` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''省级节点是否允许直接选择'' AFTER `status`');
DROP PROCEDURE `zsjos_add_area_column_if_missing`;

UPDATE `system_area`
SET `selection_code`=CAST(`id` AS CHAR), `updater`='migration-V013', `update_time`=NOW()
WHERE (`selection_code` IS NULL OR `selection_code`='')
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V013');

SET @ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.statistics
          WHERE table_schema=DATABASE() AND table_name='system_area' AND index_name='uk_parent_selection_code'),
  'SELECT 1',
  'ALTER TABLE `system_area` ADD UNIQUE KEY `uk_parent_selection_code` (`parent_id`,`selection_code`,`deleted`)');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `system_area`
SET `leaf_selectable`=b'1', `updater`='migration-V013', `update_time`=NOW()
WHERE `id` IN (810000,820000)
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V013');

INSERT IGNORE INTO `system_area`
(`id`,`name`,`selection_code`,`type`,`parent_id`,`sort`,`status`,`leaf_selectable`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(900000011,'其他','OTHER',3,110000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000012,'其他','OTHER',3,120000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000013,'其他','OTHER',3,130000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000014,'其他','OTHER',3,140000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000015,'其他','OTHER',3,150000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000021,'其他','OTHER',3,210000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000022,'其他','OTHER',3,220000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000023,'其他','OTHER',3,230000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000031,'其他','OTHER',3,310000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000032,'其他','OTHER',3,320000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000033,'其他','OTHER',3,330000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000034,'其他','OTHER',3,340000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000035,'其他','OTHER',3,350000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000036,'其他','OTHER',3,360000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000037,'其他','OTHER',3,370000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000041,'其他','OTHER',3,410000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000042,'其他','OTHER',3,420000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000043,'其他','OTHER',3,430000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000044,'其他','OTHER',3,440000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000045,'其他','OTHER',3,450000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000046,'其他','OTHER',3,460000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000050,'其他','OTHER',3,500000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000051,'其他','OTHER',3,510000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000052,'其他','OTHER',3,520000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000053,'其他','OTHER',3,530000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000054,'其他','OTHER',3,540000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000061,'其他','OTHER',3,610000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000062,'其他','OTHER',3,620000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000063,'其他','OTHER',3,630000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000064,'其他','OTHER',3,640000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000065,'其他','OTHER',3,650000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(900000071,'其他','OTHER',3,710000,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(990000000,'其他','OTHER',2,1,9999,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0'),
(990000001,'其他','OTHER',3,990000000,1,0,b'0','migration-V013',NOW(),'migration-V013',NOW(),b'0');

DROP TEMPORARY TABLE IF EXISTS `tmp_zsjos_area_initial_sort`;
CREATE TEMPORARY TABLE `tmp_zsjos_area_initial_sort` (
  `id` bigint NOT NULL,
  `sort` int NOT NULL,
  PRIMARY KEY (`id`)
);

INSERT INTO `tmp_zsjos_area_initial_sort` (`id`, `sort`)
SELECT current_area.`id`, COUNT(preceding_area.`id`) + 1
FROM `system_area` current_area
LEFT JOIN `system_area` preceding_area
  ON preceding_area.`parent_id`=current_area.`parent_id`
  AND preceding_area.`deleted`=b'0'
  AND (
    CASE WHEN preceding_area.`selection_code`='OTHER' THEN 1 ELSE 0 END
      < CASE WHEN current_area.`selection_code`='OTHER' THEN 1 ELSE 0 END
    OR (
      CASE WHEN preceding_area.`selection_code`='OTHER' THEN 1 ELSE 0 END
        = CASE WHEN current_area.`selection_code`='OTHER' THEN 1 ELSE 0 END
      AND (
        CONVERT(preceding_area.`name` USING gbk) < CONVERT(current_area.`name` USING gbk)
        OR (
          CONVERT(preceding_area.`name` USING gbk) = CONVERT(current_area.`name` USING gbk)
          AND preceding_area.`id` < current_area.`id`
        )
      )
    )
  )
WHERE current_area.`deleted`=b'0'
  AND NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version`='V013')
GROUP BY current_area.`id`;

UPDATE `system_area` area_row
JOIN `tmp_zsjos_area_initial_sort` initial_sort ON initial_sort.`id`=area_row.`id`
SET area_row.`sort`=initial_sort.`sort`,
    area_row.`updater`='migration-V013',
    area_row.`update_time`=NOW();

DROP TEMPORARY TABLE `tmp_zsjos_area_initial_sort`;

INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V013','Configure area OTHER nodes, direct-select leaves, and initial pinyin order','configurable-area-other-v2');
