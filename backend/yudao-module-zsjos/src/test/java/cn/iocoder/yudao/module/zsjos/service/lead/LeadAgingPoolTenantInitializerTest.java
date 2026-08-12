package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterSchemeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterSchemeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.INBOX_AUDIENCE_AGING_POOL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadAgingPoolTenantInitializerTest {

    @InjectMocks private LeadAgingPoolTenantInitializer initializer;
    @Mock private NotifyRuleApi notifyRuleApi;
    @Mock private LeadInboxFilterSchemeMapper schemeMapper;
    @Mock private LeadInboxFilterVersionMapper versionMapper;

    @Test
    void tenantCreatedInitializesFilterAndNotificationRules() {
        when(schemeMapper.selectByAudience(INBOX_AUDIENCE_AGING_POOL)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<LeadInboxFilterSchemeDO>getArgument(0).setId(10L);
            return 1;
        }).when(schemeMapper).insert(any(LeadInboxFilterSchemeDO.class));

        initializer.onTenantCreated(new TenantCreatedEvent(20L));

        ArgumentCaptor<LeadInboxFilterSchemeDO> schemeCaptor =
                ArgumentCaptor.forClass(LeadInboxFilterSchemeDO.class);
        verify(schemeMapper).insert(schemeCaptor.capture());
        assertEquals(INBOX_AUDIENCE_AGING_POOL, schemeCaptor.getValue().getAudience());
        assertTrue(schemeCaptor.getValue().getPublishedConfigJson().contains("waiting_assignment"));
        ArgumentCaptor<LeadInboxFilterVersionDO> versionCaptor =
                ArgumentCaptor.forClass(LeadInboxFilterVersionDO.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals(10L, versionCaptor.getValue().getSchemeId());
        assertEquals(1, versionCaptor.getValue().getVersionNo());
        ArgumentCaptor<List<NotifyDefaultRuleReqDTO>> rulesCaptor = ArgumentCaptor.forClass(List.class);
        verify(notifyRuleApi).initializeDefaultRules(rulesCaptor.capture());
        assertEquals(6, rulesCaptor.getValue().size());
        assertEquals("advance", rulesCaptor.getValue().get(0).getTimingStage());
        assertEquals(10080, rulesCaptor.getValue().get(0).getTimingOffsetMinutes());
    }

    @Test
    void tenantCreatedDoesNotDuplicateExistingFilter() {
        when(schemeMapper.selectByAudience(INBOX_AUDIENCE_AGING_POOL))
                .thenReturn(new LeadInboxFilterSchemeDO());

        initializer.onTenantCreated(new TenantCreatedEvent(20L));

        verify(schemeMapper, never()).insert(any(LeadInboxFilterSchemeDO.class));
        verify(versionMapper, never()).insert(any(LeadInboxFilterVersionDO.class));
        verify(notifyRuleApi).initializeDefaultRules(any());
    }
}
