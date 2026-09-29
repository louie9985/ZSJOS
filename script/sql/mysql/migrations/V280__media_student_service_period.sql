-- UTF-8. Migration-Owner: ai
-- Prerequisites/order: cloud Core V279 in both ledgers, zsjos_person and the unique media-student page menu.
-- Scope: one person-level flag (all existing/new people default true), one button metadata row,
-- and version records. No role assignments, business deletion or service lifecycle changes.
-- Repeatability: add column only when absent; retain every existing true/false and menu setting.
-- Rollback: revert application code; retain column, flags, menu and ledgers to avoid losing manual choices.
-- Run after V279 on installed environments; fresh baseline already includes the column.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS zsjos_v280_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v280_apply()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V279')
      OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V279') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V280 requires cloud Core V279 in both ledgers';
  END IF;
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-student:query-my' AND type=2 AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V280 requires exactly one active media student page';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_person' AND column_name='in_service_period') THEN
    ALTER TABLE zsjos_person ADD COLUMN in_service_period bit(1) NOT NULL DEFAULT b'1'
      COMMENT '是否在服务期（学员列表人工归类）' AFTER person_no;
  END IF;
END$$
DELIMITER ;
CALL zsjos_v280_apply();
DROP PROCEDURE zsjos_v280_apply;

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,
    status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '调整学员服务期','zsjos:media-student:update-service-period',3,100,p.id,'','','',NULL,
    0,b'1',b'1',b'0','migration-V280',NOW(),'migration-V280',NOW(),b'0'
FROM system_menu p
WHERE p.permission='zsjos:media-student:query-my' AND p.type=2 AND p.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu m
    WHERE m.permission='zsjos:media-student:update-service-period' AND m.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V280','Manual media student service period',SHA2('V280__media_student_service_period.sql',256))
ON DUPLICATE KEY UPDATE version=VALUES(version);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES ('core','V280','Manual media student service period',SHA2('V280__media_student_service_period.sql',256),'baseline')
ON DUPLICATE KEY UPDATE version=VALUES(version);
