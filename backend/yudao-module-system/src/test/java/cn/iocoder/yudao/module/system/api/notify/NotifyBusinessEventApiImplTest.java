package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.service.notify.NotifyBusinessEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotifyBusinessEventApiImplTest {

    @InjectMocks
    private NotifyBusinessEventApiImpl api;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private NotifyBusinessEventProcessor eventProcessor;

    @Test
    void publishUsesExplicitTenantWithoutThreadTenantContext() {
        api.publish(NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:1").targetRuleId(20L).build());

        ArgumentCaptor<NotifyBusinessEvent> captor = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(10L, captor.getValue().getTenantId());
        assertEquals("event:1", captor.getValue().getSourceEventKey());
        assertEquals(20L, captor.getValue().getTargetRuleId());
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
