-- V088: preserve the explicit new-media provider for newly created sales-self leads.
-- Dependencies/order: apply after V087; requires zsjos_lead.
-- Data scope: additive nullable column only. Existing Lead rows are not backfilled because
-- historical source_user_id values do not identify whether a provider was selected.
-- Repeatability: guarded ALTER TABLE; safe to rerun.
-- Recovery: forward-only; dropping the column would discard the new provider distinction.

SET @zsjos_v088_has_provider_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'zsjos_lead'
    AND column_name = 'source_provider_user_id'
);
SET @zsjos_v088_sql := IF(@zsjos_v088_has_provider_column = 0,
  'ALTER TABLE `zsjos_lead` ADD COLUMN `source_provider_user_id` bigint DEFAULT NULL COMMENT ''销售自拓时关联的新媒体提供方用户编号'' AFTER `source_user_id`',
  'SELECT 1');
PREPARE zsjos_v088_stmt FROM @zsjos_v088_sql;
EXECUTE zsjos_v088_stmt;
DEALLOCATE PREPARE zsjos_v088_stmt;

SET @zsjos_v088_has_recorded_column := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'zsjos_lead'
    AND column_name = 'source_provider_recorded'
);
SET @zsjos_v088_sql := IF(@zsjos_v088_has_recorded_column = 0,
  'ALTER TABLE `zsjos_lead` ADD COLUMN `source_provider_recorded` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否按销售自拓提供方规则记录'' AFTER `source_provider_user_id`',
  'SELECT 1');
PREPARE zsjos_v088_stmt FROM @zsjos_v088_sql;
EXECUTE zsjos_v088_stmt;
DEALLOCATE PREPARE zsjos_v088_stmt;

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
SELECT 'V088', 'lead source provider identity', 'V088__lead_source_provider_identity.sql'
WHERE NOT EXISTS (SELECT 1 FROM `zsjos_schema_version` WHERE `version` = 'V088');
