-- Read-only preflight. Expected result: zero rows.
-- Any row is a historical conflict requiring manual investigation; this script never repairs data.
SELECT cycle.tenant_id,
       cycle.lead_id,
       cycle.id AS aging_pool_cycle_id,
       cycle.status AS aging_pool_status,
       manual.id AS manual_public_sea_record_id
FROM zsjos_lead_aging_pool_cycle cycle
JOIN zsjos_lead_public_sea_record manual
  ON manual.tenant_id = cycle.tenant_id
 AND manual.lead_id = cycle.lead_id
 AND manual.deleted = b'0'
WHERE cycle.deleted = b'0'
  AND cycle.status IN ('waiting_assignment', 'assigned', 'deal_pending')
ORDER BY cycle.tenant_id, cycle.lead_id, cycle.id;
