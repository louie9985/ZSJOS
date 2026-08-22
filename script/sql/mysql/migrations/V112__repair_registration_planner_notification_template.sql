-- V112: repair the planner-assignment template variable contract.
-- Dependencies: V082, V085 and V087 notification migrations.
-- Scope: only the V082 system-owned template and rows still owned by the migration chain.
-- Repeatability: code, scene, creator and known migration updaters prevent duplicate or user-owned changes.
-- Recovery: forward-only; delivered message snapshots are preserved.

UPDATE `system_notify_template`
SET `title`='客资{{lead.no}}已分配给你。',
    `summary`='客资{{lead.no}}已分配给你。',
    `content`='客资{{lead.no}}已分配给你。',
    `params`='["registration.caseId","lead.no"]',
    `updater`='migration-V112',
    `update_time`=NOW()
WHERE `code`='ZSJOS_REGISTRATION_PLANNER_ASSIGNED'
  AND `scene_code`='zsjos.registration.planner_assigned'
  AND `creator`='migration-V082'
  AND `deleted`=b'0'
  AND (`updater` IN ('migration-V082','migration-V085','migration-V087') OR `updater` IS NULL);

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V112','Repair registration planner notification template','V112__repair_registration_planner_notification_template.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V112','Repair registration planner notification template',
        SHA2('V112__repair_registration_planner_notification_template.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);
