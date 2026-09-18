-- UTF-8. New Partner profile capability, following V261; requires zsjos_partner and version registries.
-- Scope: one nullable nickname column only, all tenants; no name backfill, data edits or role grants.
-- Apply before backend/H5 deployment. Existing/new unset nicknames stay NULL; leaderboard uses 未设置昵称.
-- Repeatable: column existence guard and idempotent version records. No baseline or prior migration changes.
-- Rollback: revert application while retaining this additive column; dropping it would lose user nicknames.
SET NAMES utf8mb4;
SET @partner_nickname_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='zsjos_partner' AND column_name='nickname')=0,
  'ALTER TABLE `zsjos_partner` ADD COLUMN `nickname` varchar(100) COLLATE utf8mb4_unicode_ci NULL COMMENT ''排行榜昵称'' AFTER `name`',
  'SELECT 1');
PREPARE partner_nickname_stmt FROM @partner_nickname_ddl;
EXECUTE partner_nickname_stmt;
DEALLOCATE PREPARE partner_nickname_stmt;
INSERT IGNORE INTO zsjos_schema_version(version,description,checksum)
VALUES('V262','兼职姓名与排行榜昵称分离',SHA2('V262__partner_nickname.sql',256));
INSERT IGNORE INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version,installed_at)
VALUES('core','V262','兼职姓名与排行榜昵称分离',SHA2('V262__partner_nickname.sql',256),'baseline',NOW());
