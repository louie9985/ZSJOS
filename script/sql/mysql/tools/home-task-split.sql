-- UTF-8. Local development correction, approved 2026-10-29.
-- Prerequisite: existing zsjos_business_task; run after deploying no-deadline assist creation.
-- Scope: tenant 1, nondeleted pending lead_submitter_assist only.
-- Repeatable; no schema/version change. Backup id/due_at/version before execution.
-- Recovery: restore saved due_at only for rows still pending at the resulting version.
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE zsjos_business_task SET due_at=NULL, version=version+1
WHERE tenant_id=1 AND deleted=0 AND status='pending'
AND task_type='lead_submitter_assist' AND due_at IS NOT NULL;
COMMIT;
SELECT COUNT(*) AS remaining_scheduled FROM zsjos_business_task
WHERE tenant_id=1 AND deleted=0 AND status='pending'
AND task_type='lead_submitter_assist' AND due_at IS NOT NULL;
