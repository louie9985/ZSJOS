package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;

/** Shared relation SQL keeps tenant, logical deletion and same-record matching consistent across fields. */
final class AdvancedFilterRelations {
    private AdvancedFilterRelations() {}

    static final String personFromLead = "SELECT 1 FROM zsjos_person p WHERE p.id=l.person_id AND p.tenant_id=l.tenant_id AND p.deleted=b'0'";
    static final String personFromOrder = "SELECT 1 FROM zsjos_person p WHERE p.id=o.person_id AND p.tenant_id=o.tenant_id AND p.deleted=b'0'";
    static final String personFromAppeal = "SELECT 1 FROM zsjos_lead al JOIN zsjos_person p ON p.id=al.person_id AND p.deleted=b'0' WHERE al.id=a.lead_id AND al.tenant_id=a.tenant_id AND al.deleted=b'0'";
    static final String personFromRegistration = "SELECT 1 FROM zsjos_order ro JOIN zsjos_person p ON p.id=ro.person_id AND p.deleted=b'0' WHERE ro.id=rc.order_id AND ro.tenant_id=rc.tenant_id AND ro.deleted=b'0'";
    static final String leadFromOrder = "SELECT 1 FROM zsjos_lead rl WHERE rl.id=o.lead_id AND rl.tenant_id=o.tenant_id AND rl.deleted=b'0'";
    static final String leadFromAppeal = "SELECT 1 FROM zsjos_lead rl WHERE rl.id=a.lead_id AND rl.tenant_id=a.tenant_id AND rl.deleted=b'0'";
    static final String leadFromRegistration = "SELECT 1 FROM zsjos_order ro JOIN zsjos_lead rl ON rl.id=ro.lead_id AND rl.deleted=b'0' WHERE ro.id=rc.order_id AND ro.tenant_id=rc.tenant_id AND ro.deleted=b'0'";
    static final String leadFromStudent = "SELECT 1 FROM zsjos_lead rl WHERE rl.person_id=p.id AND rl.tenant_id=p.tenant_id AND rl.deleted=b'0'";
    static final String orderFromLead = "SELECT 1 FROM zsjos_order ro LEFT JOIN zsjos_order_item oi ON oi.order_id=ro.id AND oi.tenant_id=ro.tenant_id AND oi.deleted=b'0' WHERE ro.person_id=l.person_id AND ro.tenant_id=l.tenant_id AND ro.deleted=b'0'";
    static final String orderFromAppeal = "SELECT 1 FROM zsjos_lead al JOIN zsjos_order ro ON ro.person_id=al.person_id AND ro.tenant_id=al.tenant_id AND ro.deleted=b'0' LEFT JOIN zsjos_order_item oi ON oi.order_id=ro.id AND oi.tenant_id=ro.tenant_id AND oi.deleted=b'0' WHERE al.id=a.lead_id AND al.tenant_id=a.tenant_id AND al.deleted=b'0'";
    static final String orderFromRegistration = "SELECT 1 FROM zsjos_order ro LEFT JOIN zsjos_order_item oi ON oi.order_id=ro.id AND oi.tenant_id=ro.tenant_id AND oi.deleted=b'0' WHERE ro.id=rc.order_id AND ro.tenant_id=rc.tenant_id AND ro.deleted=b'0'";
    static final String serviceFromStudent = "SELECT 1 FROM zsjos_service_relation sr JOIN zsjos_order ro ON ro.id=sr.order_id AND ro.tenant_id=sr.tenant_id AND ro.deleted=b'0' LEFT JOIN zsjos_order_item oi ON oi.id=sr.order_item_id AND oi.tenant_id=sr.tenant_id AND oi.deleted=b'0' LEFT JOIN zsjos_registration_case_route scr ON scr.registration_case_id=sr.registration_case_id AND scr.assignee_user_id=#{query.parameters.userId} AND scr.selected=b'1' AND scr.tenant_id=sr.tenant_id AND scr.deleted=b'0' WHERE sr.person_id=p.id AND sr.tenant_id=p.tenant_id AND sr.deleted=b'0' AND (#{query.parameters.tenantReadAll}=TRUE OR (sr.status='active' AND (sr.owner_user_id=#{query.parameters.userId} OR scr.id IS NOT NULL)))";
    static final String itemFromOrder = "SELECT 1 FROM zsjos_order_item oi WHERE oi.order_id=o.id AND oi.tenant_id=o.tenant_id AND oi.deleted=b'0'";

    static Map<String, Binding> leadBind(String column, String order, String appeal, String registration, String student) {
        return bind("lead", "l." + column, null, "order", "rl." + column, order, "lead_appeal", "rl." + column, appeal, "registration", "rl." + column, registration, "student", "rl." + column, student);
    }
    static Map<String, Binding> leadSubmitterFilterBind(String order, String appeal, String registration, String student) {
        String leadExpression = "CASE WHEN l.source_provider_recorded = b'1' AND l.source_type IN ('sales_self_sourced','education_self_sourced') THEN l.source_provider_user_id ELSE l.source_user_id END";
        String orderExpression = "CASE WHEN rl.source_provider_recorded = b'1' AND rl.source_type IN ('sales_self_sourced','education_self_sourced') THEN rl.source_provider_user_id ELSE rl.source_user_id END";
        return bind("lead", leadExpression, null, "order", orderExpression, order,
                "lead_appeal", orderExpression, appeal, "registration", orderExpression, registration,
                "student", orderExpression, student);
    }
    static Map<String, Binding> orderBind(String column, String lead, String appeal, String registration, String student) {
        return bind("lead", "ro." + column, lead, "order", "o." + column, null, "lead_appeal", "ro." + column, appeal, "registration", "ro." + column, registration, "student", "ro." + column, student);
    }
    static Map<String, Binding> orderExpression(String pattern, String lead, String appeal, String registration, String student) {
        return bind("lead", pattern.formatted("ro", "ro"), lead, "order", pattern.formatted("o", "o"), null, "lead_appeal", pattern.formatted("ro", "ro"), appeal, "registration", pattern.formatted("ro", "ro"), registration, "student", pattern.formatted("ro", "ro"), student);
    }
    static String opportunityFrom(String personExpression, String tenantExpression) { return "SELECT 1 FROM zsjos_opportunity op WHERE op.person_id=" + personExpression + " AND op.tenant_id=" + tenantExpression + " AND op.deleted=b'0'"; }
    static String json(String key) { return "JSON_UNQUOTE(JSON_EXTRACT(dr.submission_snapshot, '$." + key + "'))"; }

}
