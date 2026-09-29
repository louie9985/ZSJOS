-- UTF-8. V285: order-owned evidence for imported historical actor names, after Core V284.
-- Scope: one nullable JSON column and both version ledgers. No business-row backfill or grants.
-- Repeatable, including recovery after partial DDL. MySQL DDL is not transactionally reversible.
-- Deploy schema before backend. Roll back application first; retain the additive column/evidence.
-- Legacy data recovery is a separately reviewed operation using tools/recover_order_actors.py.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_v285_apply;
DELIMITER $$
CREATE PROCEDURE zsjos_v285_apply()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V284')
     OR NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V284') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V285 requires Core V284';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('zsjos_order','zsjos_schema_version','zsjos_module_schema_version')
      AND engine='InnoDB') <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V285 requires order and transactional ledgers';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_order' AND column_name='imported_actor_snapshot') THEN
    ALTER TABLE zsjos_order ADD COLUMN imported_actor_snapshot JSON DEFAULT NULL
      COMMENT '导入订单人员展示证据及来源';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_order' AND column_name='imported_actor_snapshot'
      AND data_type='json' AND is_nullable='YES' AND column_default IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V285 imported actor column contract mismatch';
  END IF;
  START TRANSACTION;
  INSERT INTO zsjos_schema_version(version,description,checksum)
  SELECT 'V285','Imported order actor evidence',SHA2('V285__order_imported_actor_snapshot.sql',256)
  WHERE NOT EXISTS (SELECT 1 FROM zsjos_schema_version WHERE version='V285');
  INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
  SELECT 'core','V285','Imported order actor evidence',SHA2('V285__order_imported_actor_snapshot.sql',256),'baseline'
  WHERE NOT EXISTS (SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V285');
  COMMIT;
END$$
DELIMITER ;
CALL zsjos_v285_apply();
DROP PROCEDURE zsjos_v285_apply;
