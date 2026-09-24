package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class OpportunityFilterFields {
    private OpportunityFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, text(Sensitivity.STANDARD, "opportunity.expectedProduct", PRODUCT, "预计产品", bind("lead", "op.expected_product_summary", opportunityFrom("l.person_id", "l.tenant_id"), "order", "op.expected_product_summary", opportunityFrom("o.person_id", "o.tenant_id"))));
        add(result, select(Sensitivity.STANDARD, "opportunity.status", STATUS, "推进状态", options(LeadConstants.OPPORTUNITY_STATUS_OPEN, "待推进", LeadConstants.OPPORTUNITY_STATUS_FOLLOWING, "跟进中", LeadConstants.OPPORTUNITY_STATUS_LOST, "已流失", LeadConstants.OPPORTUNITY_STATUS_DEAL_PENDING_APPROVAL, "成交审批中", LeadConstants.OPPORTUNITY_STATUS_WON, "已赢单"), bind("lead", "op.status", opportunityFrom("l.person_id", "l.tenant_id"), "order", "op.status", opportunityFrom("o.person_id", "o.tenant_id"))));
        add(result, text(Sensitivity.FREE_TEXT, "opportunity.lostReason", EXTRA, "流失原因", bind("lead", "op.lost_reason", opportunityFrom("l.person_id", "l.tenant_id"), "order", "op.lost_reason", opportunityFrom("o.person_id", "o.tenant_id"))));
    }
}
