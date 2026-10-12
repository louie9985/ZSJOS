package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Historical provider identity must come from recorded attribution, not current employee roles. */
final class LeadProviderDisplay {
    private LeadProviderDisplay() {}

    static String identityLabel(LeadDO lead) {
        var source = LeadSourceInterpretation.resolve(lead);
        if (PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType()) && lead.getProviderOwnerId() != null) {
            return "兼职";
        }
        if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType()) && lead.getProviderOwnerId() != null) {
            if (!source.needsReview() && (SOURCE_INTERNAL_NEW_MEDIA.equals(lead.getSourceType())
                    || source.state() == LeadSourceInterpretation.State.LINKED)) {
                return "新媒体提供方";
            }
            return "员工（身份待核实）";
        }
        // A missing provider alone cannot prove that an old self-sourced record was unassisted.
        if (source.unlinked()) {
            if (SOURCE_SALES_SELF.equals(lead.getSourceType())) return "销售本人（未关联新媒体）";
            if (SOURCE_EDUCATION_SELF.equals(lead.getSourceType())) return "教务本人（未关联新媒体）";
        }
        return "提供方待核实";
    }
}
