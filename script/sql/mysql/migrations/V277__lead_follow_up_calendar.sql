-- UTF-8. Migration-Owner: ai
-- Scope: one Workbench page under the existing /calendar directory; no role grants or business rows.
-- Prerequisites/order: V276, System menus and both schema version tables. Run using an utf8mb4 client.
-- Repeatability: insert missing metadata only; retain administrator edits and existing version checksums.
-- Rollback: retain metadata/history; roll back application code and disable the page through System management.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS zsjos_v277_preflight;
DELIMITER $$
CREATE PROCEDURE zsjos_v277_preflight()
BEGIN
  IF (SELECT COUNT(*) FROM system_menu WHERE path='/calendar' AND type=1 AND deleted=0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V277 requires exactly one active calendar directory';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V276') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V277 requires Core V276';
  END IF;
END$$
DELIMITER ;
CALL zsjos_v277_preflight();
DROP PROCEDURE zsjos_v277_preflight;

INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,workbench_render_mode,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '销售客资跟进日历','zsjos:lead-follow-up-calendar:query',2,5,p.id,'sales-lead-follow-up','ep:calendar','zsjos/leadFollowUpCalendar/index','ZsjosLeadFollowUpCalendar','native',0,b'1',b'1',b'0','V277','V277',b'0'
FROM system_menu p WHERE p.path='/calendar' AND p.type=1 AND p.deleted=0
AND NOT EXISTS(SELECT 1 FROM system_menu m WHERE m.permission='zsjos:lead-follow-up-calendar:query' AND m.deleted=0);

INSERT INTO zsjos_schema_version(version,description,checksum)
VALUES('V277','Sales Lead follow-up calendar',SHA2('V277__lead_follow_up_calendar.sql',256))
ON DUPLICATE KEY UPDATE description=VALUES(description);
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES('core','V277','Sales Lead follow-up calendar',SHA2('V277__lead_follow_up_calendar.sql',256),'baseline')
ON DUPLICATE KEY UPDATE description=VALUES(description);
