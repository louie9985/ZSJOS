-- UTF-8. Fresh-only after 00-bootstrap-schema.sql and before the baseline seeds.
-- V323 historically validates width 32. Keep its applied identity unchanged;
-- the normal V341 upgrade restores the canonical width 128 later in the chain.
-- No rows, permissions or version records are changed. DDL commits implicitly.
-- Repeated pre-seed execution is safe; any installed/data-bearing database is rejected.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_fresh_compatibility;
DELIMITER $$
CREATE PROCEDURE zsjos_fresh_compatibility()
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_schema_version') THEN
  IF EXISTS(SELECT 1 FROM zsjos_schema_version) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fresh compatibility rejects installed versions';
  END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_module_schema_version') THEN
  IF EXISTS(SELECT 1 FROM zsjos_module_schema_version) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fresh compatibility rejects installed module versions';
  END IF;
 END IF;
 IF EXISTS(SELECT 1 FROM system_users)
 OR EXISTS(SELECT 1 FROM zsjos_student_info_image)
 OR EXISTS(SELECT 1 FROM zsjos_student_info_form) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fresh compatibility requires empty pre-seed baseline';
 END IF;
 ALTER TABLE zsjos_student_info_image MODIFY COLUMN content_type VARCHAR(32) NOT NULL;
END$$
DELIMITER ;
CALL zsjos_fresh_compatibility();
DROP PROCEDURE zsjos_fresh_compatibility;
