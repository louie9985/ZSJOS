-- UTF-8. V267: additive multi-post eligibility for user relation scenes.
-- Prerequisites: V266 schema, including user relation scene and both schema version tables.
-- Order: execute before deploying backend and both frontends together.
-- Scope: two nullable JSON columns; no business rows, grants or historical scripts changed.
-- Repeatability: guard each column, then upsert version records. No historical backfill.
-- Rollback: retain columns. After multi-post saves, old code ignores extra posts;
-- reverting then requires a separately reviewed configuration rollback plan.
SET NAMES utf8mb4;

SET @relation_ddl = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_user_relation_scene' AND column_name='source_post_codes')=0,
  'ALTER TABLE zsjos_user_relation_scene ADD COLUMN source_post_codes JSON NULL COMMENT ''Configured source post codes''', 'SELECT 1');
PREPARE relation_stmt FROM @relation_ddl;
EXECUTE relation_stmt;
DEALLOCATE PREPARE relation_stmt;

SET @relation_ddl = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='zsjos_user_relation_scene' AND column_name='target_post_codes')=0,
  'ALTER TABLE zsjos_user_relation_scene ADD COLUMN target_post_codes JSON NULL COMMENT ''Configured target post codes''', 'SELECT 1');
PREPARE relation_stmt FROM @relation_ddl;
EXECUTE relation_stmt;
DEALLOCATE PREPARE relation_stmt;

INSERT INTO zsjos_schema_version(version, description, checksum, installed_at)
VALUES ('V267', 'User relation multi-post eligibility', SHA2('V267__user_relation_multi_post.sql', 256), NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum);
INSERT INTO zsjos_module_schema_version(module_code, version, description, checksum, release_version, installed_at)
VALUES ('core', 'V267', 'User relation multi-post eligibility', SHA2('V267__user_relation_multi_post.sql', 256), 'baseline', NOW())
ON DUPLICATE KEY UPDATE description=VALUES(description), checksum=VALUES(checksum), release_version=VALUES(release_version);
