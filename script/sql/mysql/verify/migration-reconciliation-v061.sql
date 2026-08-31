-- Read-only verification for the repaired V001-V061 Core migration state.
-- Every result row should report PASS.

WITH RECURSIVE expected_versions AS (
  SELECT 1 version_number
  UNION ALL
  SELECT version_number + 1 FROM expected_versions WHERE version_number < 61
), expected AS (
  SELECT CONCAT('V', LPAD(version_number, 3, '0')) version FROM expected_versions
)
SELECT 'legacy_registry_v001_v061' check_name, 61 expected, COUNT(legacy.version) actual,
       IF(COUNT(legacy.version) = 61, 'PASS', 'FAIL') result
FROM expected
LEFT JOIN zsjos_schema_version legacy ON legacy.version = expected.version
UNION ALL
SELECT 'module_registry_v001_v061', 61, COUNT(module_version.version),
       IF(COUNT(module_version.version) = 61, 'PASS', 'FAIL')
FROM expected
LEFT JOIN zsjos_module_schema_version module_version
  ON module_version.module_code = 'core' AND module_version.version = expected.version;

SELECT 'legacy_versions_missing_from_module' check_name, 0 expected, COUNT(*) actual,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') result
FROM zsjos_schema_version legacy
LEFT JOIN zsjos_module_schema_version module_version
  ON module_version.module_code = 'core' AND BINARY module_version.version = BINARY legacy.version
WHERE legacy.version REGEXP '^V[0-9]{3}$' AND module_version.version IS NULL;

SELECT 'v048_personnel_menu_6850' check_name, 1 expected, COUNT(*) actual,
       IF(COUNT(*) = 1, 'PASS', 'FAIL') result
FROM system_menu
WHERE id = 6850 AND permission = 'zsjos:personnel:query'
  AND component = 'zsjos/personnel/index' AND deleted = b'0'
UNION ALL
SELECT 'v061_supervisor_menu_6856', 1, COUNT(*), IF(COUNT(*) = 1, 'PASS', 'FAIL')
FROM system_menu
WHERE id = 6856 AND permission = 'zsjos:sales-order:supervisor-confirm'
  AND component = 'zsjos/salesOrderSupervisorConfirmation/index' AND deleted = b'0'
UNION ALL
SELECT 'v061_supervisor_menu_role_grants', 0, COUNT(*), IF(COUNT(*) = 0, 'PASS', 'FAIL')
FROM system_role_menu
WHERE menu_id = 6856 AND deleted = b'0';

SELECT 'v003_route_permission_split' check_name, 1 expected, COUNT(*) actual,
       IF(COUNT(*) = 1, 'PASS', 'FAIL') result
FROM system_menu
WHERE id = 6749 AND permission = '' AND component = 'zsjos/leadClaimPool/index'
  AND component_name = 'ZsjosLeadClaimPool' AND deleted = b'0'
UNION ALL
SELECT 'v003_claim_action_button', 1, COUNT(*), IF(COUNT(*) = 1, 'PASS', 'FAIL')
FROM system_menu
WHERE id = 6772 AND parent_id = 6749 AND permission = 'zsjos:lead:claim'
  AND type = 3 AND deleted = b'0';

SELECT 'v047_preserved_string_snapshots' check_name, 2 expected, COUNT(*) actual,
       IF(COUNT(*) = 2, 'PASS', 'FAIL') result
FROM zsjos_lead_inbox_filter_version
WHERE creator = 'migration-V047' AND version_no = 2 AND deleted = b'0'
  AND JSON_TYPE(JSON_EXTRACT(config_json, '$.groups[1].options')) = 'STRING'
UNION ALL
SELECT 'v059_corrected_array_snapshots', 2, COUNT(*), IF(COUNT(*) = 2, 'PASS', 'FAIL')
FROM zsjos_lead_inbox_filter_version
WHERE creator = 'migration-V059' AND version_no = 3 AND deleted = b'0'
  AND JSON_TYPE(JSON_EXTRACT(config_json, '$.groups[1].options')) = 'ARRAY'
UNION ALL
SELECT 'v059_active_array_schemes', 2, COUNT(*), IF(COUNT(*) = 2, 'PASS', 'FAIL')
FROM zsjos_lead_inbox_filter_scheme
WHERE deleted = b'0' AND audience IN ('submitter', 'owner') AND published_version = 3
  AND updater = 'migration-V059'
  AND JSON_TYPE(JSON_EXTRACT(published_config_json, '$.groups[1].options')) = 'ARRAY'
  AND JSON_TYPE(JSON_EXTRACT(draft_config_json, '$.groups[1].options')) = 'ARRAY'
  AND JSON_CONTAINS(JSON_EXTRACT(published_config_json, '$.groups[1].options[*].key'),
                    JSON_QUOTE('first_follow_pending'))
  AND JSON_CONTAINS(JSON_EXTRACT(published_config_json, '$.groups[1].options[*].key'),
                    JSON_QUOTE('qualification_pending'));

SELECT 'v056_required_tables' check_name, 2 expected, COUNT(*) actual,
       IF(COUNT(*) = 2, 'PASS', 'FAIL') result
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('zsjos_order_no_daily_counter', 'system_notify_business_outbox')
UNION ALL
SELECT 'v056_system_user_generated_columns', 2, COUNT(*), IF(COUNT(*) = 2, 'PASS', 'FAIL')
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'system_users'
  AND column_name IN ('unique_username', 'unique_mobile')
UNION ALL
SELECT 'v056_system_user_unique_indexes', 2, COUNT(DISTINCT index_name),
       IF(COUNT(DISTINCT index_name) = 2, 'PASS', 'FAIL')
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'system_users'
  AND index_name IN ('uk_tenant_username_binary', 'uk_tenant_mobile_active') AND non_unique = 0
UNION ALL
SELECT 'v057_runtime_columns', 2, COUNT(*), IF(COUNT(*) = 2, 'PASS', 'FAIL')
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'zsjos_lead_follow_up_rule'
  AND column_name IN ('notification_popup_duration_minutes', 'duplicate_auto_resolution_enabled');
