-- UTF-8. Requires the V269 baseline and the existing media student page permission.
-- Scope: one button metadata row and version records; no role assignments or business rows.
-- Execute after V269. Repeated execution preserves the existing permission definition.
-- Rollback: retain metadata and version records; reverting code removes the read capability.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS zsjos_v270_check;
DELIMITER $$
CREATE PROCEDURE zsjos_v270_check()
BEGIN
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-student:query-my' AND type=2 AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V270 requires exactly one active media student page';
  END IF;
END$$
DELIMITER ;
CALL zsjos_v270_check();
DROP PROCEDURE zsjos_v270_check;

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,
                         status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '新媒体学员查看全部','zsjos:media-student:query-all',3,99,p.id,'','','',NULL,
       0,b'1',b'1',b'0','migration-V270',NOW(),'migration-V270',NOW(),b'0'
FROM system_menu p
WHERE p.permission='zsjos:media-student:query-my' AND p.type=2 AND p.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission='zsjos:media-student:query-all' AND existing.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V270','Media student read-all permission',SHA2('V270__media_student_read_all.sql',256))
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES ('core','V270','Media student read-all permission',SHA2('V270__media_student_read_all.sql',256),'baseline')
ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum);
