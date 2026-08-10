package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotifyBusinessEventApiImplTest {

    @InjectMocks
    private NotifyBusinessEventApiImpl api;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void publishUsesExplicitTenantWithoutThreadTenantContext() {
        api.publish(NotifyBusinessEvent.builder()
                .tenantId(10L).sceneCode("test.scene").sourceEventKey("event:1").build());

        ArgumentCaptor<NotifyBusinessEvent> captor = ArgumentCaptor.forClass(NotifyBusinessEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(10L, captor.getValue().getTenantId());
        assertEquals("event:1", captor.getValue().getSourceEventKey());
    }
}
