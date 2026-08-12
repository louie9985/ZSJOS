-- Run with the MySQL client from the repository root:
--   mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/bootstrap.sql
-- This is a fresh-environment bootstrap. It never drops a database or table.
DROP PROCEDURE IF EXISTS `zsjos_assert_empty_database`;
DELIMITER $$
CREATE PROCEDURE `zsjos_assert_empty_database`()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Bootstrap requires an empty database; use a versioned migration for existing environments';
  END IF;
END$$
DELIMITER ;
CALL `zsjos_assert_empty_database`();
DROP PROCEDURE `zsjos_assert_empty_database`;

SOURCE script/sql/mysql/00-bootstrap-schema.sql;
SOURCE script/sql/mysql/01-bootstrap-system-seed.sql;
SOURCE script/sql/mysql/02-bootstrap-zsjos-seed.sql;
SOURCE script/sql/mysql/03-bootstrap-dictionary-types.sql;
SOURCE script/sql/mysql/migrations/V031__timed_business_notifications.sql;
SOURCE script/sql/mysql/migrations/V032__normalize_lead_inbox_filter_keys.sql;
SOURCE script/sql/mysql/migrations/V033__lead_aging_collaboration_pool.sql;
SOURCE script/sql/mysql/migrations/V034__cancel_invalid_lead_pending_tasks.sql;
SOURCE script/sql/mysql/migrations/V012__system_area_management.sql;
SOURCE script/sql/mysql/migrations/V013__configurable_area_other_nodes.sql;

INSERT IGNORE INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core', `version`, `description`, SHA2(COALESCE(`checksum`, `version`), 256), 'baseline', `installed_at`
FROM `zsjos_schema_version`;
