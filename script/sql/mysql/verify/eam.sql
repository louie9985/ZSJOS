-- EAM module verification. Read-only: this file only SELECTs.
-- Run after installing the eam module to confirm structure and baseline metadata are present.
-- Every check returns a row whose `result` column is either 'OK' or a FAIL description.

SELECT 'eam tables' AS check_name,
       CASE WHEN COUNT(*) = 24 THEN 'OK'
            ELSE CONCAT('FAIL: expected 24 required EAM tables, found ', COUNT(*)) END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('eam_category','eam_category_field','eam_asset','eam_asset_change_log',
                     'eam_transfer','eam_inventory','eam_inventory_detail','eam_repair',
                     'eam_scrap','eam_code_rule','eam_demand','eam_demand_item','eam_purchase',
                     'eam_purchase_item','eam_purchase_source','eam_receipt','eam_receipt_item',
                     'eam_stock_balance','eam_stock_movement','eam_stock_reservation','eam_stock_holding',
                     'eam_stock_reminder','eam_employee_asset_task','eam_employee_asset_task_item');

SELECT 'eam category policies' AS check_name,
       CASE WHEN COUNT(*) = 2 THEN 'OK'
            ELSE 'FAIL: category delivery/custody policy columns are missing' END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'eam_category'
  AND column_name IN ('delivery_mode','custody_mode');

SELECT 'eam stock match uniqueness' AS check_name,
       CASE WHEN GROUP_CONCAT(column_name ORDER BY seq_in_index) =
                      'tenant_id,category_id,unit,management_mode,delivery_mode,custody_mode,attribute_signature,deleted'
            THEN 'OK' ELSE 'FAIL: stock match unique index is missing policy snapshot columns' END AS result
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'eam_stock_balance'
  AND index_name = 'uk_eam_stock_balance_match';

SELECT 'eam reminder idempotency' AS check_name,
       CASE WHEN GROUP_CONCAT(column_name ORDER BY seq_in_index) =
                      'tenant_id,scene,business_type,business_id,reminder_date,deleted'
            THEN 'OK' ELSE 'FAIL: reminder unique index is missing business type' END AS result
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'eam_stock_reminder'
  AND index_name = 'uk_eam_stock_reminder';

SELECT 'eam lifecycle event idempotency' AS check_name,
       CASE WHEN COUNT(DISTINCT index_name) = 2 THEN 'OK'
            ELSE 'FAIL: employee lifecycle origin/latest event unique indexes are missing' END AS result
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'eam_employee_asset_task'
  AND index_name IN ('uk_eam_employee_asset_task_event','uk_eam_employee_asset_task_latest_event');

SELECT 'eam procurement custom-field snapshots' AS check_name,
       CASE WHEN COUNT(*) = 6 THEN 'OK'
            ELSE CONCAT('FAIL: expected 6 procurement custom-field snapshot columns, found ', COUNT(*)) END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (table_name, column_name) IN (
    ('eam_demand_item', 'ext_field_dict_types'),
    ('eam_purchase_item', 'ext_field_dict_types'),
    ('eam_stock_balance', 'ext_field_dict_types'),
    ('eam_receipt_item', 'actual_ext_fields'),
    ('eam_receipt_item', 'actual_ext_field_labels'),
    ('eam_receipt_item', 'actual_ext_field_dict_types')
  );

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

SELECT 'eam employee ownership columns' AS check_name,
       CASE WHEN COUNT(*) = 15 THEN 'OK'
            ELSE CONCAT('FAIL: expected 15 employee ownership columns, found ', COUNT(*)) END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'eam_asset' AND column_name IN ('use_employee_id','supervisor_employee_id'))
    OR (table_name = 'eam_asset_change_log' AND column_name IN ('before_employee_id','after_employee_id'))
    OR (table_name = 'eam_transfer' AND column_name IN ('from_employee_id','to_employee_id'))
    OR (table_name = 'eam_inventory_detail' AND column_name IN ('expect_employee_id','actual_employee_id'))
    OR (table_name = 'eam_asset_handover' AND column_name IN ('from_employee_id','to_employee_id'))
    OR (table_name = 'eam_purchase_source' AND column_name = 'target_employee_id')
    OR (table_name = 'eam_stock_reservation' AND column_name = 'target_employee_id')
    OR (table_name = 'eam_stock_holding' AND column_name = 'employee_id')
    OR (table_name = 'eam_employee_asset_task' AND column_name = 'employee_id')
    OR (table_name = 'eam_employee_asset_task_item' AND column_name = 'transfer_to_employee_id'));

SELECT 'eam legacy asset ownership columns' AS check_name,
       CASE WHEN COUNT(*) = 0 THEN 'OK'
            ELSE CONCAT('FAIL: legacy user ownership columns remain: ', GROUP_CONCAT(CONCAT(table_name, '.', column_name))) END AS result
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'eam_asset' AND column_name IN ('use_user_id','use_user_name_snapshot','supervisor_user_id'))
    OR (table_name = 'eam_asset_change_log' AND column_name IN ('before_user_id','after_user_id'))
    OR (table_name = 'eam_transfer' AND column_name IN ('from_user_id','to_user_id'))
    OR (table_name = 'eam_inventory_detail' AND column_name IN ('expect_user_id','actual_user_id'))
    OR (table_name = 'eam_asset_handover' AND column_name IN ('from_user_id','to_user_id'))
    OR (table_name IN ('eam_purchase_source','eam_stock_reservation') AND column_name = 'target_user_id')
    OR (table_name = 'eam_stock_holding' AND column_name = 'user_id')
    OR (table_name = 'eam_employee_asset_task' AND column_name = 'user_id')
    OR (table_name = 'eam_employee_asset_task_item' AND column_name = 'transfer_to_user_id'));

SELECT 'eam menus' AS check_name,
       CASE WHEN COUNT(*) >= 60 THEN 'OK'
            ELSE CONCAT('FAIL: expected at least 60 EAM menu and permission rows, found ',
                        COUNT(*)) END AS result
FROM `system_menu`
WHERE (`id` BETWEEN 7100 AND 7205) AND `deleted` = b'0';

SELECT 'eam dictionary types' AS check_name,
       CASE WHEN COUNT(*) = 8 THEN 'OK'
            ELSE CONCAT('FAIL: expected 8 EAM dictionary types, found ', COUNT(*)) END AS result
FROM `system_dict_type`
WHERE `type` LIKE 'eam\_%' AND `deleted` = b'0';

SELECT 'eam dictionary options' AS check_name,
       CASE WHEN COUNT(*) >= 36 THEN 'OK'
            ELSE CONCAT('FAIL: expected at least 36 EAM dictionary options, found ', COUNT(*)) END AS result
FROM `system_dict_data`
WHERE `dict_type` LIKE 'eam\_%' AND `deleted` = b'0';

SELECT 'eam supplier-return asset status' AS check_name,
       CASE WHEN COUNT(*) = 1 THEN 'OK'
            ELSE 'FAIL: eam_asset_status value 8 (已退供应商) is missing or duplicated' END AS result
FROM `system_dict_data`
WHERE `dict_type`='eam_asset_status' AND `value`='8' AND `label`='已退供应商' AND `deleted`=b'0';

SELECT 'eam default code rule' AS check_name,
       CASE WHEN COUNT(*) >= 1 THEN 'OK'
            ELSE 'FAIL: no global asset-code rule; asset creation will fail' END AS result
FROM `eam_code_rule`
WHERE `category_id` IS NULL AND `deleted` = b'0';
