package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRuleRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRuleUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;

public interface LeadFollowUpRuleService {
    LeadFollowUpRuleRespVO getRule();
    void updateRule(LeadFollowUpRuleUpdateReqVO reqVO);
    LeadFollowUpRuleDO requireEnabledRule();
}
