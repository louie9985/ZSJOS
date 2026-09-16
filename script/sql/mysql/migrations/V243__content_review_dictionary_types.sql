-- V243: create the administrator-maintained content-review dictionary types.
-- UTF-8. Exact data scope: two system_dict_type rows only; no system_dict_data rows.
-- Prerequisites: V242 and the existing System dictionary tables. V242 removes registry rows from
-- the retired V243-V249 attempts before this reviewed sequence resumes at V243.
-- Repeatability: inserts only when no active row with the same stable type exists.
-- Existing active administrator-maintained rows are preserved without label/status rewrites.
-- Rollback limitation: retain the types once business records may snapshot their values; disable
-- them through dictionary administration instead of deleting historical meaning.
SET NAMES utf8mb4;

INSERT INTO `system_dict_type`
  (`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT seed.`name`, seed.`type`, 0, seed.`remark`, 'V243', NOW(), 'V243', NOW(), b'0'
FROM (
  SELECT '作品目的' AS `name`, 'zsjos_content_purpose' AS `type`,
         'ZSJOS 内容审核作品目的，管理员维护' AS `remark`
  UNION ALL
  SELECT '作品形式', 'zsjos_content_format',
         'ZSJOS 内容审核作品形式，管理员维护'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `system_dict_type` existing
  WHERE existing.`type` = seed.`type` AND existing.`deleted` = b'0'
);
