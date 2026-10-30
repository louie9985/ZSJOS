-- Run with the MySQL client from the repository root:
--   mysql --default-character-set=utf8mb4 --skip-force -u USER -p DATABASE < script/sql/mysql/bootstrap.sql
-- This is the fresh-environment *baseline*: schema DDL plus the reviewed seed data. It never
-- drops a database or table.
--
-- It lists no versioned migration. Empty and non-empty environments are carried forward by the
-- same executor:
--   script/sql/mysql/tools/zsjos_db.py migrate <environment>   (or deploy/production/zsjos-db)
-- which applies the baseline here on a fresh database and then applies every pending migration
-- from the module manifest, recording their checksums as it goes. A migration added later is
-- picked up by that executor without editing this file, so the two paths cannot drift.

SET NAMES utf8mb4;

-- Run direct bootstrap exclusively against an empty target database. Never use --force:
-- it continues after SQL errors, even inside nested SOURCE files. --skip-force also
-- overrides a force setting inherited from mysql option files.
-- Both checks are read-only; a permanent guard routine would itself change a rejected
-- database. The invalid JSON on refusal is intentional: MySQL batch mode must fail
-- before any SOURCE can execute persistent DDL/DML. TABLES includes views.
SELECT CASE WHEN DATABASE() IS NULL
       THEN 'ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE: no database selected; select an empty database before bootstrap'
       ELSE 'ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE: selected database contains tables/views/routines/events; use the migration runner for existing installations'
       END AS bootstrap_error
WHERE DATABASE() IS NULL
   OR EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE())
   OR EXISTS (SELECT 1 FROM information_schema.routines WHERE routine_schema=DATABASE())
   OR EXISTS (SELECT 1 FROM information_schema.events WHERE event_schema=DATABASE());

SELECT JSON_EXTRACT(IF(
    DATABASE() IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE())
    AND NOT EXISTS (SELECT 1 FROM information_schema.routines WHERE routine_schema=DATABASE())
    AND NOT EXISTS (SELECT 1 FROM information_schema.events WHERE event_schema=DATABASE()),
    '{}', 'ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE'), '$') AS bootstrap_preflight;

-- Core baseline: schema, System seed, ZSJOS seed and dictionaries.
SOURCE script/sql/mysql/00-bootstrap-schema.sql;
SOURCE script/sql/mysql/bootstrap-compatibility.sql;
SOURCE script/sql/mysql/01-bootstrap-system-seed.sql;
SOURCE script/sql/mysql/02-bootstrap-zsjos-seed.sql;
SOURCE script/sql/mysql/03-bootstrap-dictionary-types.sql;
SOURCE script/sql/mysql/04-bootstrap-zsjos-feedback-dictionary.sql;
