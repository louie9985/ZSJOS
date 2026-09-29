-- UTF-8. Read-only verification after V280; no role assignments or business changes.
SET NAMES utf8mb4;
SELECT column_type,is_nullable,column_default,HEX(column_comment)
FROM information_schema.columns WHERE table_schema=DATABASE()
  AND table_name='zsjos_person' AND column_name='in_service_period';
SELECT tenant_id,COUNT(*) AS people,SUM(in_service_period=b'1') AS in_period,
  SUM(in_service_period=b'0') AS outside_period,SUM(in_service_period IS NULL) AS missing
FROM zsjos_person GROUP BY tenant_id;
SELECT m.permission,m.type,m.parent_id,p.permission AS parent_permission,HEX(m.name)
FROM system_menu m JOIN system_menu p ON p.id=m.parent_id
WHERE m.permission='zsjos:media-student:update-service-period' AND m.deleted=b'0';
SELECT version FROM zsjos_schema_version WHERE version='V280';
SELECT module_code,version FROM zsjos_module_schema_version WHERE module_code='core' AND version='V280';
