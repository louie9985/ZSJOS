-- Optional read-only administrator audit, not migration acceptance.
-- Historical role expectations below are review guidance only; migrations assign no roles.
SET NAMES utf8mb4;
SELECT r.code,m.permission,COUNT(*) active_grants
FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=b'0'
WHERE r.code IN ('center_head','dept_manager','content_director','new_media_operator','sales_specialist')
  AND m.permission IN ('zsjos:lead:submit','zsjos:lead:self-sourced:create')
GROUP BY r.code,m.permission ORDER BY r.code,m.permission;
SELECT IF(NOT EXISTS(
 SELECT 1 FROM system_role r JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
 JOIN system_menu m ON m.id=rm.menu_id AND m.permission='zsjos:lead:self-sourced:create' AND m.deleted=b'0'
 WHERE r.code<>'sales_specialist' AND r.deleted=b'0'), 'PASS','FAIL') AS self_sourced_is_sales_only;
