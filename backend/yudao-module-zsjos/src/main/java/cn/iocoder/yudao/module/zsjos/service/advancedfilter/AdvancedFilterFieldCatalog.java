package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;

/** Composes server-owned definitions; scene bindings remain the query whitelist for both frontends. */
final class AdvancedFilterFieldCatalog {
    private AdvancedFilterFieldCatalog() {}

    static final Set<String> SCENES = Set.of("lead", "order", "lead_appeal", "duplicate_review", "registration", "student", "subordinate_sales", "cashback", "withdrawal");

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
        SubordinateSalesFilterFields.register(result);
        CashbackFilterFields.register(result);
        WithdrawalFilterFields.register(result);
        return Collections.unmodifiableMap(result);
    }
}
