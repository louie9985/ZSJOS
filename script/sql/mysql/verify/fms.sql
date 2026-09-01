SELECT 'fms tables' AS check_name,
       CASE WHEN COUNT(*) = 30 THEN 'OK'
            ELSE CONCAT('FAIL: expected 30 FMS tables, found ', COUNT(*)) END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name LIKE 'fms\\_%';

SELECT 'fms account set table' AS check_name,
       CASE WHEN COUNT(*) = 1 THEN 'OK' ELSE 'FAIL: fms_account_set is missing' END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'fms_account_set';

SELECT 'fms dictionary types' AS check_name,
       CASE WHEN COUNT(*) >= 1 THEN 'OK' ELSE 'FAIL: FMS dictionary values are missing' END AS result
FROM system_dict_type
WHERE deleted = b'0' AND type LIKE 'fms\\_%';
