-- V119: normalize Workbench child page paths under the /zsjos root.
-- Forward-only and repeatable. It changes menu metadata only; no grants or business rows change.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v119_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v119_apply`()
BEGIN
  DECLARE v119_zsjos_menu_id bigint;

  IF (SELECT COUNT(*) FROM `system_menu`
      WHERE `path`='/zsjos' AND `parent_id`=0 AND `status`=0 AND `deleted`=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V119 blocked: active /zsjos root menu is missing or ambiguous';
  END IF;
  SELECT `id` INTO v119_zsjos_menu_id FROM `system_menu`
  WHERE `path`='/zsjos' AND `parent_id`=0 AND `status`=0 AND `deleted`=b'0'
  LIMIT 1;

  IF EXISTS (SELECT 1
      FROM `system_menu` prefixed_menu
      JOIN `system_menu` relative_menu
        ON relative_menu.parent_id=prefixed_menu.parent_id
       AND relative_menu.id<>prefixed_menu.id
       AND relative_menu.path=SUBSTRING(prefixed_menu.path,8)
       AND relative_menu.deleted=b'0'
      WHERE prefixed_menu.parent_id=v119_zsjos_menu_id
        AND prefixed_menu.type=2 AND prefixed_menu.deleted=b'0'
        AND prefixed_menu.path LIKE '/zsjos/%') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V119 blocked: normalized Workbench child path conflicts with an active sibling';
  END IF;

  UPDATE `system_menu`
  SET `path`=SUBSTRING(`path`,8),`updater`='migration-V119',`update_time`=NOW()
  WHERE `parent_id`=v119_zsjos_menu_id AND `type`=2 AND `deleted`=b'0'
    AND `path` LIKE '/zsjos/%';

  INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
  VALUES ('V119','Workbench relative child menu paths','workbench-relative-child-paths-v1')
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
  INSERT INTO `zsjos_module_schema_version`
    (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
  VALUES ('core','V119','Workbench relative child menu paths',
          SHA2('workbench-relative-child-paths-v1',256),'legacy',NOW())
  ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
END$$
DELIMITER ;

CALL `zsjos_v119_apply`();
DROP PROCEDURE IF EXISTS `zsjos_v119_apply`;
