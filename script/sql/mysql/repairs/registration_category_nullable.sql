-- UTF-8. Target: only zsjos_registration_class_assignment.category_id.
-- Requires V188 in both Core ledgers; restores V188's nullable-category contract.
-- Run before the reviewed V295 notice-ledger recovery and subsequent migrations.
-- No business rows, permissions or version records are inserted/deleted/updated.
-- Repeatable from NOT NULL or already NULL; stop the SQL client on any error.
-- ALTER commits implicitly. Reverting to NOT NULL requires checking later NULL rows;
-- retain the pre-recovery backup and do not truncate or invent category values.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DROP PROCEDURE IF EXISTS zsjos_recover_registration_category;
DELIMITER $$
CREATE PROCEDURE zsjos_recover_registration_category()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V188')
     OR NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V188') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Registration category recovery requires both V188 ledgers';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_registration_class_assignment' AND column_name='category_id'
    AND column_type='bigint' AND column_default IS NULL AND extra='' AND column_comment='') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Registration category shape differs from reviewed recovery';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_registration_class_assignment' AND column_name='category_id' AND is_nullable='NO') THEN
    ALTER TABLE zsjos_registration_class_assignment MODIFY COLUMN category_id bigint DEFAULT NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_registration_class_assignment' AND column_name='category_id' AND is_nullable='YES') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Registration category recovery postcondition failed';
  END IF;
END$$
DELIMITER ;
CALL zsjos_recover_registration_category();
DROP PROCEDURE zsjos_recover_registration_category;
