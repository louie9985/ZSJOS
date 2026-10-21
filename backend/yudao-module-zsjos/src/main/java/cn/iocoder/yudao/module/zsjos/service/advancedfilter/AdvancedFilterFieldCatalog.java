package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;

/** Composes server-owned definitions; scene bindings remain the query whitelist for both frontends. */
final class AdvancedFilterFieldCatalog {
    private AdvancedFilterFieldCatalog() {}

    static final Set<String> SCENES = Set.of("lead", "order", "lead_appeal", "duplicate_review", "registration", "student", "student_service", "media_student", "subordinate_sales", "education_team", "cashback", "withdrawal");

    static Map<String, Field> fields() {
        Map<String, Field> result = new LinkedHashMap<>();
        CommonFilterFields.register(result);
        LeadFilterFields.register(result);
        OpportunityFilterFields.register(result);
        OrderFilterFields.register(result);
        AppealFilterFields.register(result);
        DuplicateReviewFilterFields.register(result);
        RegistrationFilterFields.register(result);
        StudentFilterFields.register(result);
        MediaStudentFilterFields.register(result);
        SubordinateSalesFilterFields.register(result);
        EducationTeamFilterFields.register(result);
        CashbackFilterFields.register(result);
        WithdrawalFilterFields.register(result);
        // The service query joins exactly one person/order/item/lead. Reuse field identities
        // while removing the person-wide EXISTS relation that could match a sibling course.
        result.replaceAll((key, field) -> {
            Binding old = field.bindings().get("student");
            if (old == null) return field;
            var bindings = new LinkedHashMap<>(field.bindings());
            String expression = old.expression();
            if ("person.name".equals(key)) expression = "sr.student_name_snapshot";
            if ("lead.lastFollowUpAt".equals(key)) expression = "(SELECT MAX(cr.submitted_at) FROM zsjos_student_contact_record cr WHERE cr.service_relation_id=sr.id AND cr.tenant_id=sr.tenant_id AND cr.deleted=0)";
            if ("lead.nextFollowUpAt".equals(key)) expression = "(SELECT MIN(bt.due_at) FROM zsjos_business_task bt WHERE bt.biz_type='student_service' AND bt.biz_id=sr.id AND bt.tenant_id=sr.tenant_id AND bt.deleted=0 AND bt.status='pending')";
            if ("orderItem.product".equals(key)) expression = "COALESCE(oi.product_snapshot,sr.service_snapshot)";
            bindings.put("student_service", new Binding(expression, null));
            return new Field(field.sensitivity(), field.key(), field.group(), field.label(), field.type(),
                    field.operators(), field.optionSource(), field.options(), bindings);
        });
        return Collections.unmodifiableMap(result);
    }
}
