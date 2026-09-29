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

    @Test void durablePublishReportsConfiguredRulesWithoutSynchronousDelivery() {
        var inApp = NotifyRuleDO.builder().id(1L).channelCode("in_app").build();
        var wecom = NotifyRuleDO.builder().id(2L).channelCode("wecom").build();
        var sms = NotifyRuleDO.builder().id(3L).channelCode("sms").build();
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(java.util.List.of(inApp, wecom, sms));
        var event = NotifyBusinessEvent.builder().tenantId(10L).sceneCode("test.scene").sourceEventKey("durable")
                .recipientMode("FIXED").fixedRecipients(java.util.List.of(cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO.admin(7L))).build();
        assertEquals(2, api.publishDurable(event));
        var captured = org.mockito.ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(outboxService).enqueue(captured.capture(), org.mockito.ArgumentMatchers.eq(java.util.List.of(inApp, wecom)));
        assertEquals(event.getFixedRecipients(), captured.getValue().getFixedRecipients());
        org.mockito.Mockito.verifyNoInteractions(eventProcessor, applicationEventPublisher);
    }

    @Test void durablePublishWithoutRulesReturnsZero() {
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(java.util.List.of());
        assertEquals(0, api.publishDurable(NotifyBusinessEvent.builder().tenantId(10L).sceneCode("test.scene").build()));
        org.mockito.Mockito.verifyNoInteractions(outboxService, eventProcessor, applicationEventPublisher);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void fixedRecipientsSurviveNormalizationAndRuleCopy(boolean confirmed) {
        var rule = NotifyRuleDO.builder().id(21L).sceneCode("test.scene").channelCode("wecom").build();
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(java.util.List.of(rule));
        var recipients = java.util.List.of(
                cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO.admin(7L),
                cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO.partner(7L));
        var event = NotifyBusinessEvent.builder().tenantId(10L).sceneCode("test.scene").sourceEventKey("fixed")
                .recipientMode("FIXED").fixedRecipients(recipients).build();
        if (confirmed) api.publishConfirmed(event); else api.publish(event);
        var captured = org.mockito.ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(outboxService).enqueue(captured.capture(), org.mockito.ArgumentMatchers.eq(java.util.List.of(rule)));
        assertEquals("FIXED", captured.getValue().getRecipientMode());
        assertEquals(recipients, captured.getValue().getFixedRecipients());
        if (confirmed) assertEquals(21L, captured.getValue().getTargetRuleId());
    }

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
    void publishRoutesSmsAfterCommitEventInsteadOfOutbox() {
        NotifyRuleDO rule = NotifyRuleDO.builder().id(21L).sceneCode("test.scene").channelCode("sms").build();
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
    void wecomUsesOutboxAndNeverAfterCommitBestEffort() {
        NotifyRuleDO rule = NotifyRuleDO.builder().id(21L).sceneCode("test.scene").channelCode("wecom").build();
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(java.util.List.of(rule));
        api.publish(NotifyBusinessEvent.builder().tenantId(10L).sceneCode("test.scene").sourceEventKey("e").build());
        verify(outboxService).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(java.util.List.of(rule)));
        org.mockito.Mockito.verifyNoInteractions(applicationEventPublisher, eventProcessor);
    }

    @Test
    void confirmedWecomReminderConfirmsDurableAcceptanceNotDirectSend() {
        NotifyRuleDO rule = NotifyRuleDO.builder().id(21L).sceneCode("test.scene").channelCode("wecom").build();
        when(notifyRuleService.getEnabledRules("test.scene")).thenReturn(java.util.List.of(rule));
        var result = api.publishConfirmed(NotifyBusinessEvent.builder().tenantId(10L).sceneCode("test.scene")
                .targetRuleId(21L).sourceEventKey("e").build());
        assertEquals("WECOM_QUEUED", result.getExternalId());
        verify(outboxService).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(java.util.List.of(rule)));
        org.mockito.Mockito.verifyNoInteractions(applicationEventPublisher, eventProcessor);
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
