-- V229: Sales self-sourced is independent from ordinary lead submission.
-- Scope: logically retire only the active ordinary-submit grant on sales_specialist roles.
-- No users or business rows are changed; rerunnable and forward-only.
UPDATE system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.code='sales_specialist' AND r.deleted=b'0'
JOIN system_menu m ON m.id=rm.menu_id AND m.permission='zsjos:lead:submit' AND m.deleted=b'0'
SET rm.deleted=b'1', rm.updater='migration-V229', rm.update_time=NOW()
WHERE rm.deleted=b'0';

INSERT IGNORE INTO zsjos_schema_version(version, description, checksum)
VALUES ('V229','Separate sales self-sourced permission from ordinary lead submission','sales-self-sourced-permission-separation-v1');

