-- EAM module verification. Read-only: this file only SELECTs.
-- Run after installing the eam module to confirm structure and baseline metadata are present.
-- Every check returns a row whose `result` column is either 'OK' or a FAIL description.

SELECT 'eam tables' AS check_name,
       CASE WHEN COUNT(*) = 10 THEN 'OK'
            ELSE CONCAT('FAIL: expected 10 EAM tables, found ', COUNT(*)) END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('eam_category','eam_category_field','eam_asset','eam_asset_change_log',
                     'eam_transfer','eam_inventory','eam_inventory_detail','eam_repair',
                     'eam_scrap','eam_code_rule');

SELECT 'eam_asset ext_fields column' AS check_name,
       CASE WHEN COUNT(*) = 1 THEN 'OK'
            ELSE 'FAIL: eam_asset.ext_fields is missing' END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'eam_asset' AND column_name = 'ext_fields';

SELECT 'eam_asset previous_status column' AS check_name,
       CASE WHEN COUNT(*) = 1 THEN 'OK'
            ELSE 'FAIL: eam_asset.previous_status is missing; repair/scrap rollback will break' END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'eam_asset' AND column_name = 'previous_status';

SELECT 'eam_asset unique code index' AS check_name,
       CASE WHEN COUNT(*) > 0 THEN 'OK'
            ELSE 'FAIL: uk_eam_asset_code is missing; duplicate asset codes become possible' END AS result
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'eam_asset' AND index_name = 'uk_eam_asset_code';

SELECT 'eam menus' AS check_name,
       CASE WHEN COUNT(*) >= 43 THEN 'OK'
            ELSE CONCAT('FAIL: expected 43 EAM menu rows (1 directory + 8 menus + 34 buttons), found ',
                        COUNT(*)) END AS result
FROM `system_menu`
WHERE (`id` BETWEEN 7100 AND 7199) AND `deleted` = b'0';

SELECT 'eam dictionary types' AS check_name,
       CASE WHEN COUNT(*) = 7 THEN 'OK'
            ELSE CONCAT('FAIL: expected 7 EAM dictionary types, found ', COUNT(*)) END AS result
FROM `system_dict_type`
WHERE `type` LIKE 'eam\_%' AND `deleted` = b'0';

SELECT 'eam dictionary options' AS check_name,
       CASE WHEN COUNT(*) >= 36 THEN 'OK'
            ELSE CONCAT('FAIL: expected at least 36 EAM dictionary options, found ', COUNT(*)) END AS result
FROM `system_dict_data`
WHERE `dict_type` LIKE 'eam\_%' AND `deleted` = b'0';

SELECT 'eam default code rule' AS check_name,
       CASE WHEN COUNT(*) >= 1 THEN 'OK'
            ELSE 'FAIL: no global asset-code rule; asset creation will fail' END AS result
FROM `eam_code_rule`
WHERE `category_id` IS NULL AND `deleted` = b'0';
