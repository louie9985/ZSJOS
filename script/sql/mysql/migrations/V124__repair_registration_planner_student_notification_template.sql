-- V124 repairs the planner-assignment template to use student terminology and number.
-- Scope: only the migration-owned V082 template and rows still owned by V082/V112.
-- Repeatability: administrator-edited templates are preserved; delivered messages are immutable.
-- Recovery: forward-only; historical message snapshots are not rewritten.

UPDATE `system_notify_template`
SET `title`='学员已分配给你',
    `summary`='学员{{student.name}}（{{student.no}}）已分配给你。',
    `content`='学员{{student.name}}（{{student.no}}）已分配给你。',
    `params`='["registration.caseId","student.name","student.no"]',
    `updater`='migration-V124',
    `update_time`=NOW()
WHERE `code`='ZSJOS_REGISTRATION_PLANNER_ASSIGNED'
  AND `scene_code`='zsjos.registration.planner_assigned'
  AND `creator`='migration-V082'
  AND `deleted`=b'0'
  AND (`updater` IN ('migration-V082','migration-V085','migration-V087','migration-V112') OR `updater` IS NULL);

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V124','Repair planner notification student number template',
        'V124__repair_registration_planner_student_notification_template.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V124','Repair planner notification student number template',
        SHA2('V124__repair_registration_planner_student_notification_template.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
