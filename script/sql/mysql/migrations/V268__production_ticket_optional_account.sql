-- UTF-8. V268: a production ticket may be raised without any bound account.
-- Additive upgrade after V267. Only relaxes NOT NULL; no columns, rows or grants are removed.
-- Scope: tickets started from the work-order centre are not tied to an account, while account-page
-- tickets still bind one (enforced by the service, not by the column). Existing rows are untouched.
-- Repeatable on MySQL 8: inspect the current nullability before ALTER, then record V268 after it succeeds.
-- Rollback: set the column back to NOT NULL only after every NULL row is backfilled with a real account.
SET NAMES utf8mb4;

SET @ticket_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_production_ticket' AND column_name='account_id' AND is_nullable='NO')=1,
  'ALTER TABLE zsjos_production_ticket MODIFY COLUMN account_id bigint NULL', 'SELECT 1');
PREPARE ticket_stmt FROM @ticket_ddl; EXECUTE ticket_stmt; DEALLOCATE PREPARE ticket_stmt;

INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
VALUES ('V268', 'Production ticket optional account binding', SHA2('V268__production_ticket_optional_account.sql', 256), NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
VALUES ('core', 'V268', 'Production ticket optional account binding', SHA2('V268__production_ticket_optional_account.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum), release_version=VALUES(release_version);
