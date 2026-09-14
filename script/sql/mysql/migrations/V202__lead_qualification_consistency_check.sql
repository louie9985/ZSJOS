-- UTF-8. V202: verify qualification backfill consistency without inventing history.
-- Depends on V196. Any inconsistent active lead is rejected explicitly for operator repair.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v202_check;
DELIMITER $$
CREATE PROCEDURE zsjos_v202_check()
BEGIN
  IF EXISTS (SELECT 1 FROM zsjos_lead WHERE deleted=b'0' AND status='submitted' AND assignment_status='owned' AND ownership_started_at IS NOT NULL AND qualification_deadline_at IS NULL)
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V202 found active owned leads without qualification deadline; repair source data before release'; END IF;
END$$
DELIMITER ;
CALL zsjos_v202_check();
DROP PROCEDURE IF EXISTS zsjos_v202_check;
INSERT INTO zsjos_schema_version(version,description,checksum,installed_at) VALUES ('V202','Lead qualification consistency check',SHA2('V202__lead_qualification_consistency_check.sql',256),NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at) VALUES ('core','V202','Lead qualification consistency check',SHA2('V202__lead_qualification_consistency_check.sql',256),'baseline',NOW()) ON DUPLICATE KEY UPDATE checksum=VALUES(checksum),description=VALUES(description);
