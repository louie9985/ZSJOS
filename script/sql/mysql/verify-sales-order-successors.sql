-- Read-only audit for historical sales-order duplicates and inconsistent successor chains.
-- This script does not update, delete, lock, or infer which historical order should be retained.

SELECT tenant_id, lead_id, COUNT(*) active_order_count, GROUP_CONCAT(CONCAT(id, ':', order_no, ':', status) ORDER BY id) orders
FROM zsjos_order
WHERE deleted=b'0' AND lead_id IS NOT NULL AND status IN ('pending_approval','revision_required')
GROUP BY tenant_id, lead_id HAVING COUNT(*) > 1;

SELECT tenant_id, person_id, COUNT(*) active_order_count, GROUP_CONCAT(CONCAT(id, ':', order_no, ':', status) ORDER BY id) orders
FROM zsjos_order
WHERE deleted=b'0' AND order_type='repurchase' AND status IN ('pending_approval','revision_required')
GROUP BY tenant_id, person_id HAVING COUNT(*) > 1;

SELECT old_order.tenant_id, old_order.id old_order_id, old_order.status old_status,
       old_order.superseded_by_order_id, new_order.id new_order_id, new_order.supersedes_order_id
FROM zsjos_order old_order
LEFT JOIN zsjos_order new_order ON new_order.id=old_order.superseded_by_order_id
  AND new_order.tenant_id=old_order.tenant_id AND new_order.deleted=b'0'
WHERE old_order.deleted=b'0' AND (old_order.superseded_by_order_id IS NOT NULL OR old_order.status='superseded')
  AND (old_order.status<>'superseded' OR new_order.id IS NULL OR new_order.supersedes_order_id<>old_order.id);

SELECT tenant_id, order_id, COUNT(*) pending_round_count,
       GROUP_CONCAT(CONCAT(id, ':', process_instance_id) ORDER BY id) rounds
FROM zsjos_order_approval_round
WHERE deleted=b'0' AND status='pending'
GROUP BY tenant_id, order_id HAVING COUNT(*) > 1;

SELECT o.tenant_id,o.id order_id,o.order_no,o.status,
       rc.id registration_case_id,rc.status registration_status,
       bt.id revision_task_id,bt.status revision_task_status
FROM zsjos_order o
LEFT JOIN zsjos_registration_case rc ON rc.tenant_id=o.tenant_id AND rc.order_id=o.id AND rc.deleted=b'0'
LEFT JOIN zsjos_business_task bt ON bt.tenant_id=o.tenant_id AND bt.biz_type='sales-order'
  AND bt.biz_id=o.id AND bt.task_type='sales_order_revision' AND bt.deleted=b'0' AND bt.status='pending'
WHERE o.deleted=b'0' AND o.status='superseded'
  AND (rc.id IS NOT NULL AND rc.status NOT IN ('cancelled','completed') OR bt.id IS NOT NULL);
