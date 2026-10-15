package cn.iocoder.yudao.module.zsjos.dal.mysql.registration;

/** Correlated scalar reads preserve exactly one result per service before pagination. */
public final class StudentServiceSummarySql {
    private StudentServiceSummarySql() {}
    public static String task(String column) {
        if (!java.util.Set.of("id", "due_at").contains(column)) throw new IllegalArgumentException(column);
        return "(SELECT bt." + column + " FROM zsjos_business_task bt WHERE bt.biz_type='student_service' AND bt.biz_id=sr.id AND bt.tenant_id=sr.tenant_id AND bt.deleted=0 AND bt.status='pending' AND bt.task_type IN ('student_first_contact','student_study_plan','student_contact') ORDER BY CASE bt.task_type WHEN 'student_first_contact' THEN 0 WHEN 'student_study_plan' THEN 1 ELSE 2 END,bt.id DESC LIMIT 1)";
    }
    public static final String LAST_CONTACT = "(SELECT MAX(cr.submitted_at) FROM zsjos_student_contact_record cr WHERE cr.service_relation_id=sr.id AND cr.tenant_id=sr.tenant_id AND cr.deleted=0)";
    public static final String TASK_STATE = "(CASE WHEN " + task("id") + " IS NULL THEN 'none' WHEN " + task("due_at") + " < #{query.parameters.now} THEN 'overdue' ELSE 'pending' END)";
}
