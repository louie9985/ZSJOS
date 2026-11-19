-- UTF-8. Development-only, tenant 1, ruoyi-vue-pro / yudao-mysql.
-- Prerequisite: deploy no-create UnscheduledDeliveryClassService before execution.
-- Explicit candidates audited 2026-11-16. No services or historical references are removed.
-- Backup DDL commits independently; all backup-row inserts and soft deletes share one transaction.
-- Run mysql without --force; rollback on any client/statement failure. Repeat execution changes zero rows.
-- Recovery: restore deleted/updater/update_time/version from backup for these IDs after checking no replacement class exists.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS zsjos_bak_empty_unscheduled_20261116 AS
SELECT * FROM zsjos_delivery_class WHERE 1=0;
START TRANSACTION;
SELECT id FROM zsjos_delivery_class WHERE tenant_id=1
 AND id IN (73,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129) FOR UPDATE;
INSERT INTO zsjos_bak_empty_unscheduled_20261116
SELECT c.* FROM zsjos_delivery_class c
WHERE c.tenant_id=1 AND c.deleted=0 AND c.class_kind='UNSCHEDULED'
 AND c.id IN (73,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129)
 AND NOT EXISTS (SELECT 1 FROM zsjos_service_relation s WHERE s.tenant_id=1 AND s.deleted=0 AND s.class_id=c.id)
 AND NOT EXISTS (SELECT 1 FROM zsjos_class_transfer_request t WHERE t.tenant_id=1 AND t.deleted=0 AND t.target_class_id=c.id AND t.status NOT IN ('approved','rejected','cancelled','invalidated'))
 AND NOT EXISTS (SELECT 1 FROM zsjos_bak_empty_unscheduled_20261116 b WHERE b.id=c.id);
UPDATE zsjos_delivery_class c JOIN zsjos_bak_empty_unscheduled_20261116 b ON b.id=c.id
SET c.deleted=1,c.updater='codex-retire-unscheduled-20261116',c.version=c.version+1
WHERE c.tenant_id=1 AND c.deleted=0 AND c.class_kind='UNSCHEDULED'
 AND NOT EXISTS (SELECT 1 FROM zsjos_service_relation s WHERE s.tenant_id=1 AND s.deleted=0 AND s.class_id=c.id)
 AND NOT EXISTS (SELECT 1 FROM zsjos_class_transfer_request t WHERE t.tenant_id=1 AND t.deleted=0 AND t.target_class_id=c.id AND t.status NOT IN ('approved','rejected','cancelled','invalidated'));
SELECT ROW_COUNT() AS soft_deleted_count;
COMMIT;
