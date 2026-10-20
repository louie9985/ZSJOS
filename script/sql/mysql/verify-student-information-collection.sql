-- UTF-8. Read-only verification after V323. No personal data or tokens are selected.
SET NAMES utf8mb4;
SELECT 'student_info_form_tables' AS check_name,
       (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_student_info_form','zsjos_student_info_form_value','zsjos_student_info_form_config','zsjos_student_info_config_lock','zsjos_student_info_image')) AS actual,
       5 AS expected;
SELECT 'permissions' check_name, COUNT(DISTINCT permission) actual, 11 expected FROM system_menu
WHERE permission LIKE 'zsjos:student-info-form:%' AND deleted=b'0';
SELECT 'permission_duplicates' check_name, COUNT(*) actual, 0 expected FROM (
 SELECT permission FROM system_menu WHERE permission LIKE 'zsjos:student-info-form:%' AND deleted=b'0'
 GROUP BY permission HAVING COUNT(*)>1
) duplicates;
SELECT 'registration_unique_index' check_name, COUNT(*) actual, 3 expected FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND index_name='uk_student_info_case' AND non_unique=0;
SELECT 'version_registry' check_name, COUNT(*) actual, 1 expected FROM zsjos_schema_version WHERE version='V323';
SELECT 'module_version_registry' check_name, COUNT(*) actual, 1 expected FROM zsjos_module_schema_version WHERE module_code='core' AND version='V323';
SELECT name,HEX(name) name_hex,path,component FROM system_menu WHERE permission='zsjos:student-info-form:config:query' AND deleted=b'0';
SELECT table_name,HEX(table_comment) comment_hex FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_student_info%';
