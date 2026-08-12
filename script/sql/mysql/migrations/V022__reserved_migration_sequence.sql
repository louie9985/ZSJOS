-- V022: reserve the historical migration sequence gap before V023.
-- Dependencies/order: apply after V021 and before V023.
-- Data scope: schema-version metadata only; no schema or business rows are changed.
-- Repeatability: INSERT IGNORE keeps reruns idempotent.
-- Rollback limitation: keep the version marker once later migrations are applied.
INSERT IGNORE INTO `zsjos_schema_version` (`version`,`description`,`checksum`)
VALUES ('V022','Reserve migration sequence before sales-order approval','reserved-migration-sequence-v1');
