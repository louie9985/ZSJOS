-- Encoding: UTF-8. Manual recovery only; not part of the bootstrap/migration chain.
-- Prerequisites: material tables from V194, deployed default-template initialization;
-- operator approval for shared environments and a backup of the two target type rows.
-- Scope: tenant 1, viral_account / viral_content with dangling schema references,
-- no remaining template versions (including deleted ones), and no material records.
-- No deletion, permission/dictionary changes, or historical snapshot reconstruction.
-- Order: back up target rows -> execute -> open each decomposition page to invoke
-- existing material-type/list initialization -> verify published fields and Chinese HEX.
-- Repeatability: subsequent executions do nothing once pointers are null or valid.
-- Recovery: before initialization, restore original pointers from the row backup only
-- after review; after initialization, keep generated templates and use forward repair.
-- This does not recover lost custom templates; restore those from a reviewed backup.
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE zsjos_material_type t
LEFT JOIN zsjos_material_schema_version current_schema
  ON current_schema.id = t.current_schema_version_id
  AND current_schema.tenant_id = t.tenant_id AND current_schema.deleted = b'0'
SET t.current_schema_version_id = NULL, t.version = t.version + 1,
    t.updater = 'viral-template-recovery', t.update_time = NOW()
WHERE t.tenant_id = 1 AND t.code IN ('viral_account', 'viral_content')
  AND t.deleted = b'0' AND t.current_schema_version_id IS NOT NULL
  AND current_schema.id IS NULL
  AND NOT EXISTS (SELECT 1 FROM zsjos_material_schema_version s
                  WHERE s.tenant_id = t.tenant_id AND s.material_type_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM zsjos_material m
                  WHERE m.tenant_id = t.tenant_id AND m.material_type_id = t.id);
SELECT ROW_COUNT() AS repaired_type_references;
COMMIT;
