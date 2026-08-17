package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.CREATED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_OPERATOR;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_SUBMITTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeadNotificationTenantInitializerTest {

    @InjectMocks
    private LeadNotificationTenantInitializer initializer;
    @Mock
    private NotifyRuleApi notifyRuleApi;

    @Test
    void tenantCreatedInitializesLeadCreatedRuleForProviderAndOperator() {
        initializer.onTenantCreated(new TenantCreatedEvent(20L));

        ArgumentCaptor<List<NotifyDefaultRuleReqDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(notifyRuleApi).initializeDefaultRules(captor.capture());
        assertEquals(1, captor.getValue().size());
        NotifyDefaultRuleReqDTO rule = captor.getValue().get(0);
        assertEquals(CREATED, rule.getSceneCode());
        assertEquals("ZSJOS_LEAD_CREATED", rule.getTemplateCode());
        assertEquals(List.of(ROLE_SUBMITTER, ROLE_OPERATOR), rule.getRecipientRoles());
        assertEquals(NotifyActionType.BUSINESS_DETAIL, rule.getActionType());
    }
}
