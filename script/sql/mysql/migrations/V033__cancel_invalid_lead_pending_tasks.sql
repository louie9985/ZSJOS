-- V033: cancel historical pending lifecycle tasks for leads already judged invalid.
-- Dependencies/order: apply after V008 business tasks and V014 lead qualification state.
-- Data scope: every tenant's non-deleted pending lead_first_follow_up,
-- lead_follow_up_reminder, and lead_qualification task whose non-deleted lead is invalid.
-- Repeatability: only pending rows are updated, so reruns make no further task changes.
-- Rollback limitation: no rows are deleted. Reopening a cancelled task requires a reviewed
-- forward repair based on task and lead audit history; this migration does not reactivate work.
SET time_zone = '+08:00';

UPDATE `zsjos_business_task` task
JOIN `zsjos_lead` l
  ON l.`tenant_id` = task.`tenant_id`
 AND l.`id` = task.`biz_id`
 AND l.`deleted` = b'0'
SET task.`status` = 'cancelled',
    task.`cancelled_at` = NOW(),
    task.`cancel_reason` = '历史无效客资待办清理（V033）',
    task.`updater` = 'migration-V033',
    task.`update_time` = NOW(),
    task.`version` = COALESCE(task.`version`, 0) + 1
WHERE task.`biz_type` = 'lead'
  AND task.`task_type` IN ('lead_first_follow_up', 'lead_follow_up_reminder', 'lead_qualification')
  AND task.`status` = 'pending'
  AND task.`deleted` = b'0'
  AND l.`status` = 'invalid';

INSERT INTO `zsjos_schema_version` (`version`, `description`, `checksum`)
VALUES ('V033', 'Cancel pending lifecycle tasks for invalid leads',
        'cancel-invalid-lead-pending-tasks-v1')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);
