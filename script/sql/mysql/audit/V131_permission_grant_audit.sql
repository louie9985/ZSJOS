-- Read-only V131/V134 authorization audit. Review tenant and row counts before any revocation.
SELECT rm.tenant_id,r.id AS role_id,r.code AS role_code,r.status AS role_status,
       m.id AS menu_id,m.permission,m.status AS menu_status,tp.id AS package_id,
       JSON_CONTAINS(tp.menu_ids,CAST(m.id AS JSON),'$') AS package_contains_menu,
       rm.creator,rm.create_time
FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
JOIN system_menu m ON m.id=rm.menu_id
LEFT JOIN system_tenant t ON t.id=rm.tenant_id
LEFT JOIN system_tenant_package tp ON tp.id=t.package_id AND tp.deleted=b'0'
WHERE rm.deleted=b'0' AND rm.creator IN ('V131','V134')
  AND (r.status<>0 OR r.deleted=b'1' OR m.status<>0 OR m.deleted=b'1'
       OR tp.id IS NULL OR NOT JSON_CONTAINS(tp.menu_ids,CAST(m.id AS JSON),'$'))
ORDER BY rm.tenant_id,r.id,m.id;
