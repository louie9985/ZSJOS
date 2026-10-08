package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import java.time.LocalDateTime;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Shared state restoration; caller owns the Lead lock, authorization, audit and transaction. */
final class LeadValidityRestoration {
    private LeadValidityRestoration() {}
    static void restore(LeadDO lead, Long userId, String reason, LocalDateTime now,
                        LeadMapper leads, OpportunityMapper opportunities, LeadIntendedProductMapper products) {
        OpportunityDO opportunity = opportunities.selectByLeadId(lead.getId());
        boolean create = opportunity == null;
        if (create) {
            opportunity = new OpportunityDO();
            opportunity.setType(OPPORTUNITY_TYPE_INITIAL_CONVERSION);
            opportunity.setLeadId(lead.getId());
            opportunity.setExpectedProductSummary(LeadBasicInfoService.productSummary(products.selectListByLeadId(lead.getId())));
            opportunity.setVersion(0);
        }
        opportunity.setPersonId(lead.getPersonId());
        opportunity.setOwnerUserId(lead.getOwnerUserId());
        opportunity.setStatus(OPPORTUNITY_STATUS_OPEN);
        opportunity.setLostAt(null); opportunity.setLostReason(null);
        opportunity.setNextFollowUpAt(null);
        if (create) opportunities.insert(opportunity); else opportunities.updateById(opportunity);
        if (!create) opportunities.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OpportunityDO>()
                .eq(OpportunityDO::getId, opportunity.getId()).set(OpportunityDO::getLostAt, null)
                .set(OpportunityDO::getLostReason, null).set(OpportunityDO::getNextFollowUpAt, null));
        lead.setStatus(STATUS_VALID); lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setInvalidReason(null); lead.setInvalidReasonLabelSnapshot(null);
        lead.setInvalidDescription(null); lead.setInvalidEvidenceRefs(null); lead.setAppealDeadlineAt(null);
        lead.setSuspendedAt(null); lead.setNextFollowUpAt(null);
        lead.setQualifiedByUserId(userId); lead.setQualifiedAt(now); lead.setConvertedAt(now);
        lead.setValidDescription(reason.trim());
        LeadMapper.advanceActivity(lead, now);
        leads.updateById(lead);
        // MyBatis' default update strategy ignores nulls; clearing current invalid evidence must be explicit.
        leads.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LeadDO>()
                .eq(LeadDO::getId, lead.getId()).set(LeadDO::getInvalidReason, null)
                .set(LeadDO::getInvalidReasonLabelSnapshot, null).set(LeadDO::getInvalidDescription, null)
                .set(LeadDO::getInvalidEvidenceRefs, null).set(LeadDO::getAppealDeadlineAt, null)
                .set(LeadDO::getSuspendedAt, null).set(LeadDO::getNextFollowUpAt, null));
    }
}
