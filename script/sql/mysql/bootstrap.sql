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
SOURCE script/sql/mysql/migrations/V034__lead_aging_collaboration_pool.sql;
SOURCE script/sql/mysql/migrations/V035__cancel_invalid_lead_pending_tasks.sql;
SOURCE script/sql/mysql/migrations/V036__subordinate_sales_management.sql;
SOURCE script/sql/mysql/migrations/V037__lifecycle_domain_unification.sql;
SOURCE script/sql/mysql/migrations/V038__duplicate_lead_review.sql;
SOURCE script/sql/mysql/migrations/V039__lead_pools_and_claim_limit.sql;
SOURCE script/sql/mysql/migrations/V040__submitter_actions_and_complaints.sql;
SOURCE script/sql/mysql/migrations/V041__order_repurchase_and_concurrency.sql;
SOURCE script/sql/mysql/migrations/V042__normalize_legacy_lead_filter_status.sql;
SOURCE script/sql/mysql/migrations/V043__order_lifecycle_review_fixes.sql;
SOURCE script/sql/mysql/migrations/V044__default_employee_avatar.sql;
SOURCE script/sql/mysql/migrations/V045__dual_frontend_workbench_menu_components.sql;
SOURCE script/sql/mysql/migrations/V046__customer_order_advanced_filter_indexes.sql;
SOURCE script/sql/mysql/migrations/V047__split_lead_pending_handling_stages.sql;
SOURCE script/sql/mysql/migrations/V048__account_personnel_partner_lifecycle.sql;
SOURCE script/sql/mysql/migrations/V049__maintenance_mode_and_scheduler_guard.sql;
SOURCE script/sql/mysql/migrations/V050__readonly_impersonation_and_audit_catalog.sql;
SOURCE script/sql/mysql/migrations/V051__cashback_domain.sql;
SOURCE script/sql/mysql/migrations/V012__system_area_management.sql;
SOURCE script/sql/mysql/migrations/V013__configurable_area_other_nodes.sql;

INSERT IGNORE INTO `zsjos_module_schema_version`
  (`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
SELECT 'core', `version`, `description`, SHA2(COALESCE(`checksum`, `version`), 256), 'baseline', `installed_at`
FROM `zsjos_schema_version`;
