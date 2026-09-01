package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotifyBusinessOutboxServiceTest {

    @InjectMocks private NotifyBusinessOutboxService service;
    @Mock private NotifyBusinessOutboxMapper outboxMapper;
    @Mock private NotifyBusinessEventProcessor eventProcessor;
    @Mock private NotifyMessageMapper notifyMessageMapper;

    @Test
    void permanentFailureIsNotRetriedAndUpdateUsesClaimToken() {
        NotifyBusinessOutboxDO row = row(1L, "event:1");
        when(outboxMapper.selectDue(any(), eq(100))).thenReturn(List.of(row));
        when(outboxMapper.claim(eq(1L), any(), any(), anyString())).thenReturn(1);
        when(eventProcessor.processConfirmed(any())).thenReturn(
                NotifySendResult.failure("INVALID", "invalid rule", false));

        service.deliverDue();

        ArgumentCaptor<NotifyBusinessOutboxDO> state = ArgumentCaptor.forClass(NotifyBusinessOutboxDO.class);
        ArgumentCaptor<String> claim = ArgumentCaptor.forClass(String.class);
        verify(outboxMapper).updateDeliveryState(state.capture(), claim.capture(), any());
        assertEquals("failed", state.getValue().getStatus());
        assertEquals(1, state.getValue().getAttemptCount());
        assertNull(state.getValue().getClaimToken());
        assertFalse(claim.getValue().isBlank());
    }

    @Test
    void oneDeliveryExceptionDoesNotStopTheBatch() {
        NotifyBusinessOutboxDO first = row(1L, "event:1");
        NotifyBusinessOutboxDO second = row(2L, "event:2");
        when(outboxMapper.selectDue(any(), eq(100))).thenReturn(List.of(first, second));
        when(outboxMapper.claim(anyLong(), any(), any(), anyString())).thenReturn(1);
        when(eventProcessor.processConfirmed(any()))
                .thenThrow(new IllegalStateException("temporary"))
                .thenReturn(NotifySendResult.success(null));

        service.deliverDue();

        verify(outboxMapper, times(2)).updateDeliveryState(any(), anyString(), any());
        assertEquals("failed", first.getStatus());
        assertEquals("succeeded", second.getStatus());
    }

    @Test
    void retryableResultUsesConfiguredRetrySchedule() {
        NotifyBusinessOutboxDO row = row(1L, "event:1");
        when(outboxMapper.selectDue(any(), eq(100))).thenReturn(List.of(row));
        when(outboxMapper.claim(eq(1L), any(), any(), anyString())).thenReturn(1);
        when(eventProcessor.processConfirmed(any())).thenReturn(
                NotifySendResult.failure("TEMPORARY", "recipient unavailable", true));

        service.deliverDue();

        assertEquals("pending", row.getStatus());
        assertEquals(1, row.getAttemptCount());
        assertNotNull(row.getNextAttemptAt());
    }

    private NotifyBusinessOutboxDO row(Long id, String eventKey) {
        NotifyBusinessOutboxDO row = new NotifyBusinessOutboxDO();
        row.setId(id);
        row.setTenantId(10L);
        row.setSceneCode("test.scene");
        row.setSourceEventKey(eventKey);
        row.setTargetRuleId(20L);
        row.setOccurredAt(LocalDateTime.now());
        row.setStatus("pending");
        row.setAttemptCount(0);
        row.setNextAttemptAt(LocalDateTime.now());
        return row;
    }
}
