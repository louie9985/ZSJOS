-- Run with the MySQL client from the repository root:
--   mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/bootstrap.sql
-- This is the fresh-environment *baseline*: schema DDL plus the reviewed seed data. It never
-- drops a database or table.
--
-- It lists no versioned migration. Empty and non-empty environments are carried forward by the
-- same executor:
--   script/sql/mysql/tools/zsjos_db.py migrate <environment>   (or deploy/production/zsjos-db)
-- which applies the baseline here on a fresh database and then applies every pending migration
-- from the module manifest, recording their checksums as it goes. A migration added later is
-- picked up by that executor without editing this file, so the two paths cannot drift.

-- Core baseline: schema, System seed, ZSJOS seed and dictionaries.
SOURCE script/sql/mysql/00-bootstrap-schema.sql;
SOURCE script/sql/mysql/01-bootstrap-system-seed.sql;
SOURCE script/sql/mysql/02-bootstrap-zsjos-seed.sql;
SOURCE script/sql/mysql/03-bootstrap-dictionary-types.sql;
SOURCE script/sql/mysql/04-bootstrap-zsjos-feedback-dictionary.sql;
