package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyRuleDO;
import cn.iocoder.yudao.module.system.service.notify.NotifyBusinessEventProcessor;
import cn.iocoder.yudao.module.system.service.notify.NotifyBusinessOutboxService;
import cn.iocoder.yudao.module.system.service.notify.NotifyRuleService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class NotifyBusinessEventApiImplTest {

    @InjectMocks
    private NotifyBusinessEventApiImpl api;
    @Mock
    private NotifyBusinessEventProcessor eventProcessor;
    @Mock private NotifyBusinessOutboxService outboxService;
    @Mock private NotifyRuleService notifyRuleService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void publishUsesExplicitTenantWithoutThreadTenantContext() {
        NotifyRuleDO rule = NotifyRuleDO.builder().id(20L).sceneCode("test.scene").build();
        when(notifyRuleService.getEnabledRules("test.scene")).thenAnswer(invocation -> {
            assertEquals(10L, TenantContextHolder.getRequiredTenantId());
            return java.util.List.of(rule);
        });
        api.publish(NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:1").targetRuleId(20L).build());
        verify(outboxService).enqueue(org.mockito.ArgumentMatchers.argThat(normalized ->
                normalized.getTenantId().equals(10L) && normalized.getSourceEventKey().equals("event:1")),
                org.mockito.ArgumentMatchers.eq(java.util.List.of(rule)));
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void publishRoutesExternalRuleAfterCommitEventInsteadOfOutbox() {
        NotifyRuleDO rule = NotifyRuleDO.builder().id(21L).sceneCode("test.scene").channelCode("wecom").build();
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(java.util.List.of(rule));

        api.publish(NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:external").build());

        org.mockito.ArgumentCaptor<Object> published = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(published.capture());
        NotifyBusinessEvent routed = (NotifyBusinessEvent) published.getValue();
        assertEquals(21L, routed.getTargetRuleId());
        assertEquals(10L, routed.getTenantId());
        org.mockito.Mockito.verifyNoInteractions(outboxService);
    }

    @Test
    void publishConfirmedReturnsProcessorResult() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:2").targetRuleId(20L).build();
        NotifySendResult expected = NotifySendResult.success(null);
        when(eventProcessor.processConfirmed(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        NotifySendResult actual = api.publishConfirmed(event);

        assertEquals(expected, actual);
        verify(eventProcessor).processConfirmed(org.mockito.ArgumentMatchers.argThat(normalized ->
                normalized.getTenantId().equals(10L) && normalized.getTargetRuleId().equals(20L)));
    }
}
