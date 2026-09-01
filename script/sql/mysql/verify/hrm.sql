SELECT 'hrm tables' AS check_name,
       CASE WHEN COUNT(*) = 50 THEN 'OK'
            ELSE CONCAT('FAIL: expected 50 HRM tables, found ', COUNT(*)) END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name LIKE 'hrm\\_%';

SELECT 'hrm employee table' AS check_name,
       CASE WHEN COUNT(*) = 1 THEN 'OK' ELSE 'FAIL: hrm_employee is missing' END AS result
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'hrm_employee';

SELECT 'hrm dictionary types' AS check_name,
       CASE WHEN COUNT(*) >= 1 THEN 'OK' ELSE 'FAIL: HRM dictionary values are missing' END AS result
FROM system_dict_type
WHERE deleted = b'0' AND type LIKE 'hrm\\_%';
