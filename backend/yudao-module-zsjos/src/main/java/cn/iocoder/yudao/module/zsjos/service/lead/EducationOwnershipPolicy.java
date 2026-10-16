package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Acquisition evidence shared by retained-owner restoration and education reassignment. */
public final class EducationOwnershipPolicy {
    private EducationOwnershipPolicy() {}
    public static boolean supports(LeadDO lead) {
        return SOURCE_EDUCATION_SELF.equals(lead.getSourceType())
                || SOURCE_INTERNAL_NEW_MEDIA.equals(lead.getSourceType())
                    && SOURCE_EDUCATION_SELF.equals(lead.getSubmissionSourceType())
                    && DISPATCH_SELF.equals(lead.getDispatchMode())
                || SOURCE_PARTNER.equals(lead.getSourceType())
                    && DISPATCH_SPECIFIED.equals(lead.getDispatchMode());
    }
}
