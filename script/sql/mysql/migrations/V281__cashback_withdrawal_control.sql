-- UTF-8. Migration-Owner: ai
-- Prerequisites/order: Core V280 and the unique finance cashback page. Run with utf8mb4.
-- Scope: four nullable cashback control columns, two button permission metadata rows, version records.
-- Existing cashback values remain unchanged; no role assignments or business rows are seeded.
-- Repeatability: missing-only columns/menu metadata; preserve administrator configuration and all history.
-- Rollback: revert application code only after resolving blocked records; retain control fields and audit history.
-- Fresh baseline contains these nullable columns; the same migration installs permission metadata.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v281_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v281_apply()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V280') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V281 requires Core V280';
  END IF;
  IF (SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:cashback:finance-query' AND type=2 AND deleted=b'0') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V281 requires one finance cashback page';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_cashback' AND column_name='blocked_from_status') THEN
    ALTER TABLE zsjos_cashback ADD COLUMN blocked_from_status varchar(32) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_cashback' AND column_name='block_reason') THEN
    ALTER TABLE zsjos_cashback ADD COLUMN block_reason varchar(500) DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_cashback' AND column_name='blocked_by_user_id') THEN
    ALTER TABLE zsjos_cashback ADD COLUMN blocked_by_user_id bigint DEFAULT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_cashback' AND column_name='blocked_at') THEN
    ALTER TABLE zsjos_cashback ADD COLUMN blocked_at datetime DEFAULT NULL;
  END IF;
END$$
DELIMITER ;
CALL zsjos_v281_apply();
DROP PROCEDURE zsjos_v281_apply;

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '禁止提现','zsjos:cashback:block',3,100,p.id,'','','',NULL,0,b'1',b'1',b'0','migration-V281',NOW(),'migration-V281',NOW(),b'0'
FROM system_menu p WHERE p.permission='zsjos:cashback:finance-query' AND p.type=2 AND p.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='zsjos:cashback:block' AND m.deleted=b'0');

INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT '恢复提现','zsjos:cashback:unblock',3,101,p.id,'','','',NULL,0,b'1',b'1',b'0','migration-V281',NOW(),'migration-V281',NOW(),b'0'
FROM system_menu p WHERE p.permission='zsjos:cashback:finance-query' AND p.type=2 AND p.deleted=b'0'
AND NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission='zsjos:cashback:unblock' AND m.deleted=b'0');

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES ('V281','Cashback withdrawal control',SHA2('V281__cashback_withdrawal_control.sql',256))
ON DUPLICATE KEY UPDATE version=VALUES(version);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES ('core','V281','Cashback withdrawal control',SHA2('V281__cashback_withdrawal_control.sql',256),'baseline')
ON DUPLICATE KEY UPDATE version=VALUES(version);
