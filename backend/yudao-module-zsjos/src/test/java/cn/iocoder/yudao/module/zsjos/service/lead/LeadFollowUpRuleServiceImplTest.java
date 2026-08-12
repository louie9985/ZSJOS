package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRuleUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_RULE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadFollowUpRuleServiceImplTest {
    @InjectMocks private LeadFollowUpRuleServiceImpl service;
    @Mock private LeadFollowUpRuleMapper ruleMapper;

    @Test
    void updatesTimeoutAndAdvancesVersion() {
        LeadFollowUpRuleDO rule = rule(1440, 2);
        when(ruleMapper.selectByCode("default")).thenReturn(rule);
        LeadFollowUpRuleUpdateReqVO request = new LeadFollowUpRuleUpdateReqVO();
        request.setFirstFollowUpTimeoutMinutes(720);
        request.setQualificationTimeoutMinutes(4320);
        request.setAgingPoolTimeoutDays(120);
        request.setNoProgressWarningDays(7);
        request.setNoProgressGraceDays(2);

        service.updateRule(request);

        assertEquals(720, rule.getFirstFollowUpTimeoutMinutes());
        assertEquals(120, rule.getAgingPoolTimeoutDays());
        assertEquals(7, rule.getNoProgressWarningDays());
        assertEquals(2, rule.getNoProgressGraceDays());
        assertEquals(3, rule.getVersion());
        verify(ruleMapper).updateById(rule);
    }

    @Test
    void rejectsDisabledOrOutOfRangeRule() {
        when(ruleMapper.selectByCode("default")).thenReturn(rule(4, 0));
        ServiceException error = assertThrows(ServiceException.class, service::requireEnabledRule);
        assertEquals(LEAD_FOLLOW_UP_RULE_INVALID.getCode(), error.getCode());
    }

    private static LeadFollowUpRuleDO rule(int timeout, int version) {
        LeadFollowUpRuleDO rule = new LeadFollowUpRuleDO();
        rule.setId(1L); rule.setCode("default"); rule.setName("默认首次跟进规则");
        rule.setFirstFollowUpTimeoutMinutes(timeout); rule.setQualificationTimeoutMinutes(4320);
        rule.setAgingPoolTimeoutDays(90); rule.setNoProgressWarningDays(7); rule.setNoProgressGraceDays(2);
        rule.setStatus(0); rule.setVersion(version);
        return rule;
    }
}
