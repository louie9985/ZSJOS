-- Read-only audit and recovery export for V135 inferred snapshots. No UPDATE is performed here.
SELECT id,tenant_id,creator,create_time,fields_snapshot_json,values_snapshot_json,dict_snapshot_json,
       layer1_json,layer2_json,formula_json
FROM zsjos_positioning_card
WHERE creator='V133' AND deleted=b'0'
  AND (fields_snapshot_json IS NOT NULL OR values_snapshot_json IS NOT NULL OR dict_snapshot_json IS NOT NULL
       OR layer1_json IS NOT NULL OR layer2_json IS NOT NULL OR formula_json IS NOT NULL)
ORDER BY tenant_id,id;

-- After exporting the result above and confirming the exact row count, execute the cleanup manually:
-- UPDATE zsjos_positioning_card SET fields_snapshot_json=NULL,values_snapshot_json=NULL,dict_snapshot_json=NULL,
--   layer1_json=NULL,layer2_json=NULL,formula_json=NULL,updater='V135-audited-cleanup',update_time=NOW()
-- WHERE creator='V133' AND deleted=b'0' AND id IN (<confirmed IDs>);
