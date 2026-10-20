package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

/** Rechecked by the UPDATE so a stale scan cannot cancel a task after the pool has exited. */
public final class LeadPublicSeaTaskCleanupSql {
    private LeadPublicSeaTaskCleanupSql() {}

    public static String obsolete() {
        return "t.tenant_id=#{tenantId} AND t.deleted=0 AND t.status='pending' AND t.biz_type='lead'"
                + " AND t.task_type IN ('lead_first_follow_up','lead_follow_up_reminder')"
                + " AND EXISTS (SELECT 1 FROM zsjos_lead l WHERE l.id=t.biz_id AND l.tenant_id=t.tenant_id AND l.deleted=0)"
                + " AND (EXISTS (SELECT 1 FROM zsjos_lead_public_sea_pool_cycle c WHERE c.lead_id=t.biz_id"
                + " AND c.tenant_id=t.tenant_id AND c.deleted=0 AND c.status IN ('waiting_assignment','assigned','deal_pending')"
                + " AND (t.task_type='lead_first_follow_up' OR c.status='waiting_assignment' OR c.collaborator_user_id IS NULL"
                + " OR t.assignee_id IS NULL OR t.assignee_id != c.collaborator_user_id"
                + " OR t.create_time < COALESCE(c.assigned_at,c.entered_at)))"
                + " OR EXISTS (SELECT 1 FROM zsjos_lead_public_sea_pool_compatibility_record m WHERE m.lead_id=t.biz_id"
                + " AND m.tenant_id=t.tenant_id AND m.deleted=0"
                + " AND NOT EXISTS (SELECT 1 FROM zsjos_lead_public_sea_pool_cycle c WHERE c.lead_id=m.lead_id"
                + " AND c.tenant_id=m.tenant_id AND c.deleted=0 AND c.status IN ('waiting_assignment','assigned','deal_pending'))"
                + " AND (t.task_type='lead_first_follow_up' OR m.collaborator_user_id IS NULL"
                + " OR t.assignee_id IS NULL OR t.assignee_id != m.collaborator_user_id OR t.create_time < m.released_at)))";
    }

    public static String candidates() {
        return "<script>SELECT t.id FROM zsjos_business_task t WHERE " + obsolete().replace("<", "&lt;")
                + " <if test='leadId != null'>AND t.biz_id=#{leadId}</if> ORDER BY t.id LIMIT 500</script>";
    }

    public static String cancel() {
        return "<script>UPDATE zsjos_business_task t SET status='cancelled',cancelled_at=#{now},"
                + "cancel_reason=#{reason},version=version+1,update_time=#{now},updater='system' WHERE " + obsolete().replace("<", "&lt;")
                + " AND t.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>";
    }
}
