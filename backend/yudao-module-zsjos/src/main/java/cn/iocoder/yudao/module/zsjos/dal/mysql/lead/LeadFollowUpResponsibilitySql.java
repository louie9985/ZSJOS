package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

/** Current follow-up responsibility is independent of formal ownership and historical task assignees. */
public final class LeadFollowUpResponsibilitySql {
    private LeadFollowUpResponsibilitySql() {}

    private static String cycle(String lead) {
        return " FROM zsjos_lead_public_sea_pool_cycle fc WHERE fc.lead_id=" + lead + ".id"
                + " AND fc.tenant_id=" + lead + ".tenant_id AND fc.deleted=0"
                + " AND fc.status IN ('waiting_assignment','assigned','deal_pending')";
    }

    private static String manual(String lead) {
        return " FROM zsjos_lead_public_sea_pool_compatibility_record fm WHERE fm.lead_id=" + lead + ".id"
                + " AND fm.tenant_id=" + lead + ".tenant_id AND fm.deleted=0";
    }

    public static String hasPool(String lead) {
        return "(EXISTS (SELECT 1" + cycle(lead) + ") OR EXISTS (SELECT 1" + manual(lead) + "))";
    }

    public static String responsible(String lead) {
        // A waiting cycle must yield NULL, not fall back to A. Overlapping legacy/current pools fail closed.
        return "(CASE WHEN EXISTS (SELECT 1" + cycle(lead) + ") THEN CASE"
                + " WHEN EXISTS (SELECT 1" + manual(lead) + ") THEN NULL ELSE"
                + " (SELECT CASE WHEN fc.status IN ('assigned','deal_pending') THEN fc.collaborator_user_id"
                + " ELSE NULL END" + cycle(lead) + " ORDER BY fc.id DESC LIMIT 1) END"
                + " WHEN EXISTS (SELECT 1" + manual(lead) + ") THEN"
                + " (SELECT CASE WHEN fm.owner_user_id=" + lead + ".owner_user_id THEN fm.collaborator_user_id"
                + " ELSE NULL END" + manual(lead) + " LIMIT 1) ELSE " + lead + ".owner_user_id END)";
    }

    public static String isFollowUpTask(String task) {
        return "(" + task + ".biz_type='lead' AND " + task
                + ".task_type IN ('lead_first_follow_up','lead_follow_up_reminder'))";
    }

    public static String taskAssignee(String task) {
        // Completed/cancelled tasks remain historical facts. Outside public sea, preserve the stored task assignee.
        return "(CASE WHEN " + task + ".status='pending' AND " + isFollowUpTask(task)
                + " THEN (SELECT CASE WHEN " + hasPool("fl") + " THEN " + responsible("fl")
                + " ELSE " + task + ".assignee_id END FROM zsjos_lead fl WHERE fl.id=" + task
                + ".biz_id AND fl.tenant_id=" + task + ".tenant_id AND fl.deleted=0) ELSE "
                + task + ".assignee_id END)";
    }
    public static String taskAssignees() {
        return "<script>SELECT t.id, " + taskAssignee("t")
                + " AS assignee_id FROM zsjos_business_task t WHERE t.deleted=0 AND t.tenant_id=#{tenantId}"
                + " AND t.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>";
    }
}
