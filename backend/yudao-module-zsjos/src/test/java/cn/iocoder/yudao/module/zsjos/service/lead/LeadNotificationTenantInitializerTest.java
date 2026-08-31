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
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_NEW_MEDIA_PROVIDER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.ROLE_OPERATOR;
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
        assertEquals(2, captor.getValue().size());
        NotifyDefaultRuleReqDTO salesRule = captor.getValue().get(0);
        assertEquals(CREATED, salesRule.getSceneCode());
        assertEquals("ZSJOS_LEAD_CREATED", salesRule.getTemplateCode());
        assertEquals(List.of(ROLE_OPERATOR), salesRule.getRecipientRoles());
        assertEquals(NotifyActionType.BUSINESS_DETAIL, salesRule.getActionType());
        NotifyDefaultRuleReqDTO providerRule = captor.getValue().get(1);
        assertEquals(CREATED, providerRule.getSceneCode());
        assertEquals("ZSJOS_LEAD_SOURCE_LINKED", providerRule.getTemplateCode());
        assertEquals(List.of(ROLE_NEW_MEDIA_PROVIDER), providerRule.getRecipientRoles());
        assertEquals(NotifyActionType.BUSINESS_DETAIL, providerRule.getActionType());
    }
}
