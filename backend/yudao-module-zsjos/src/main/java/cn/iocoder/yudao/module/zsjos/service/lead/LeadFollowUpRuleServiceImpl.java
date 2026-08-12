package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRuleRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRuleUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRuleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.FOLLOW_UP_RULE_DEFAULT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_RULE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_RULE_NOT_EXISTS;

@Service
public class LeadFollowUpRuleServiceImpl implements LeadFollowUpRuleService {
    @Resource private LeadFollowUpRuleMapper ruleMapper;

    @Override
    public LeadFollowUpRuleRespVO getRule() {
        LeadFollowUpRuleDO rule = requireEnabledRule();
        LeadFollowUpRuleRespVO result = new LeadFollowUpRuleRespVO();
        result.setId(rule.getId());
        result.setCode(rule.getCode());
        result.setName(rule.getName());
        result.setFirstFollowUpTimeoutMinutes(rule.getFirstFollowUpTimeoutMinutes());
        result.setQualificationTimeoutMinutes(rule.getQualificationTimeoutMinutes());
        result.setAgingPoolTimeoutDays(rule.getAgingPoolTimeoutDays());
        result.setNoProgressWarningDays(rule.getNoProgressWarningDays());
        result.setNoProgressGraceDays(rule.getNoProgressGraceDays());
        result.setStatus(rule.getStatus());
        result.setVersion(rule.getVersion());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(LeadFollowUpRuleUpdateReqVO reqVO) {
        LeadFollowUpRuleDO rule = requireEnabledRule();
        rule.setFirstFollowUpTimeoutMinutes(reqVO.getFirstFollowUpTimeoutMinutes());
        rule.setQualificationTimeoutMinutes(reqVO.getQualificationTimeoutMinutes());
        rule.setAgingPoolTimeoutDays(reqVO.getAgingPoolTimeoutDays());
        rule.setNoProgressWarningDays(reqVO.getNoProgressWarningDays());
        rule.setNoProgressGraceDays(reqVO.getNoProgressGraceDays());
        rule.setVersion(rule.getVersion() == null ? 1 : rule.getVersion() + 1);
        ruleMapper.updateById(rule);
    }

    @Override
    public LeadFollowUpRuleDO requireEnabledRule() {
        LeadFollowUpRuleDO rule = ruleMapper.selectByCode(FOLLOW_UP_RULE_DEFAULT);
        if (rule == null) throw exception(LEAD_FOLLOW_UP_RULE_NOT_EXISTS);
        Integer timeout = rule.getFirstFollowUpTimeoutMinutes();
        Integer qualificationTimeout = rule.getQualificationTimeoutMinutes();
        Integer agingPoolTimeoutDays = rule.getAgingPoolTimeoutDays();
        Integer warningDays = rule.getNoProgressWarningDays();
        Integer graceDays = rule.getNoProgressGraceDays();
        if (!CommonStatusEnum.ENABLE.getStatus().equals(rule.getStatus())
                || timeout == null || timeout < 5 || timeout > 10080
                || qualificationTimeout == null || qualificationTimeout < 5 || qualificationTimeout > 43200
                || agingPoolTimeoutDays == null || agingPoolTimeoutDays < 1 || agingPoolTimeoutDays > 3650
                || warningDays == null || warningDays < 1 || warningDays > 365
                || graceDays == null || graceDays < 1 || graceDays > 30) {
            throw exception(LEAD_FOLLOW_UP_RULE_INVALID);
        }
        return rule;
    }
}
