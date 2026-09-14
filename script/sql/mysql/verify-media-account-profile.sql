-- UTF-8. Read-only checks after V209; run using mysql --default-character-set=utf8mb4.
SET NAMES utf8mb4;
SELECT 'profile query button' AS check_name, IF(COUNT(*)=1,'PASS','FAIL') AS result
FROM system_menu m JOIN system_menu p ON p.id=m.parent_id AND p.deleted=b'0' AND p.status=0
WHERE m.permission='zsjos:media-account:query' AND m.deleted=b'0' AND m.status=0 AND m.type=3
AND p.permission='zsjos:media-student:query-my';
SELECT 'profile query label bytes' AS check_name, HEX(name) AS name_utf8_hex
FROM system_menu WHERE permission='zsjos:media-account:query' AND deleted=b'0';
SELECT 'profile entry table' AS check_name, IF(COUNT(*)=1,'PASS','FAIL') AS result
FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_media_account_profile_entry';
SELECT 'empty account columns' AS check_name, IF(COUNT(*)=4,'PASS','FAIL') AS result
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_account'
AND column_name IN ('owner_operator_user_id','platform_value','platform_label_snapshot','nickname') AND is_nullable='YES';
SELECT 'profile version' AS check_name, IF(COUNT(*)=1,'PASS','FAIL') AS result FROM zsjos_schema_version WHERE version='V209';
SELECT tenant_id,version_no,JSON_LENGTH(fields_json) AS configured_fields FROM zsjos_media_account_field_config WHERE status='published' AND deleted=b'0';
SELECT type,HEX(name) AS name_utf8_hex FROM system_dict_type WHERE type IN ('zsjos_account_publish_rhythm','zsjos_account_content_format') AND deleted=b'0';
