-- UTF-8. V265: one production ticket may freeze multiple accounts for a single work-order flow.
-- Additive upgrade after V264. No role grants or business rows are deleted. Existing rows retain account_id compatibility.
-- Scope: add two nullable snapshot columns only; no historical business data is backfilled.
-- Repeatable on MySQL 8: inspect each column before ALTER, then record V265 after both succeed.
-- Rollback: retain additive columns when reverting application code; dropping them loses new snapshots.
SET NAMES utf8mb4;

SET @ticket_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_production_ticket' AND column_name='account_ids_json')=0,
  'ALTER TABLE zsjos_production_ticket ADD COLUMN account_ids_json JSON NULL COMMENT ''Frozen related account IDs''', 'SELECT 1');
PREPARE ticket_stmt FROM @ticket_ddl; EXECUTE ticket_stmt; DEALLOCATE PREPARE ticket_stmt;
SET @ticket_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_production_ticket' AND column_name='account_snapshot_json')=0,
  'ALTER TABLE zsjos_production_ticket ADD COLUMN account_snapshot_json JSON NULL COMMENT ''Frozen account names, platforms, homepage links and cover files''', 'SELECT 1');
PREPARE ticket_stmt FROM @ticket_ddl; EXECUTE ticket_stmt; DEALLOCATE PREPARE ticket_stmt;

INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
VALUES ('V265', 'Production ticket multi-account snapshot', SHA2('V265__production_ticket_multi_account.sql', 256), NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
VALUES ('core', 'V265', 'Production ticket multi-account snapshot', SHA2('V265__production_ticket_multi_account.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum), release_version=VALUES(release_version);
