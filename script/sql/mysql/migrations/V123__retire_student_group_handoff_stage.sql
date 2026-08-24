-- V123 retires the learning-planner group/handoff stage from active service progress.
-- Dependency/order: apply after V122 and deploy before the matching application release.
-- Data scope: active and historical service-relation current projections at group_handoff only.
-- Historical student-contact records are immutable and remain unchanged.
-- Repeatability: updated rows no longer match the predicate; reruns change no business rows.
-- Recovery: use a reviewed forward migration; the removed current-stage facts are not reconstructable
-- from this migration, while immutable contact-record snapshots remain available for audit.

START TRANSACTION;

UPDATE `zsjos_service_relation`
SET `delivery_stage`='supervision',
    `delivery_data_json`=NULL,
    `version`=`version`+1,
    `updater`='migration-V123',
    `update_time`=NOW()
WHERE `deleted`=b'0'
  AND `delivery_stage`='group_handoff';

INSERT INTO `zsjos_schema_version` (`version`,`description`,`checksum`,`installed_at`)
VALUES ('V123','Retire student group handoff delivery stage',
        'V123__retire_student_group_handoff_stage.sql',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

INSERT INTO `zsjos_module_schema_version`
(`module_code`,`version`,`description`,`checksum`,`release_version`,`installed_at`)
VALUES ('core','V123','Retire student group handoff delivery stage',
        SHA2('V123__retire_student_group_handoff_stage.sql',256),'baseline',NOW())
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`checksum`=VALUES(`checksum`);

COMMIT;
