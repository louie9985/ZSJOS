-- UTF-8. Read-only verification after the V006 IT serial-field development correction.
-- Scope: tenant 1; does not modify schema, version records or business data.
SET NAMES utf8mb4;
WITH RECURSIVE it_categories AS (
  SELECT id,parent_id,code FROM eam_category WHERE code='IT' AND parent_id=0 AND tenant_id=1 AND deleted=b'0'
  UNION ALL
  SELECT c.id,c.parent_id,c.code FROM eam_category c JOIN it_categories p ON c.parent_id=p.id
  WHERE c.tenant_id=1 AND c.deleted=b'0'
)
SELECT c.id,c.code,f.field_key,HEX(f.field_name) AS field_name_hex
FROM it_categories c JOIN eam_category root ON root.code='IT' AND root.parent_id=0 AND root.tenant_id=1 AND root.deleted=b'0'
JOIN eam_category_field f ON f.category_id=root.id AND f.tenant_id=1 AND f.field_key='sn' AND f.deleted=b'0'
ORDER BY c.id;
-- Expected: exactly one configured IT root field, TEXT=1, optional and visible; Chinese HEX E5BA8FE58897E58FB7.
SELECT f.category_id,f.field_type,f.required,f.admin_visible,f.collection_visible,HEX(f.field_name)
FROM eam_category_field f JOIN eam_category c ON c.id=f.category_id AND c.tenant_id=f.tenant_id
WHERE c.code='IT' AND c.parent_id=0 AND c.tenant_id=1 AND c.deleted=b'0' AND f.field_key='sn' AND f.deleted=b'0';
