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
    @Mock private WecomOutboxDeliveryService wecomDeliveryService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void inapplicableRuleIsTerminalSkippedWithAuditableReason(boolean wecom) {
        var row = row(1L, "event:skip"); row.setAttemptCount(2);
        if (wecom) row.setPayload(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(new WecomOutboxPayload()));
        when(outboxMapper.selectDue(any(), eq(100))).thenReturn(List.of(row));
        when(outboxMapper.claim(eq(1L), any(), any(), anyString())).thenReturn(1);
        when(outboxMapper.selectClaimed(eq(1L), anyString())).thenAnswer(call -> { row.setClaimToken(call.getArgument(1)); return row; });
        var skipped = NotifySendResult.skipped("LEAD_SOURCE_LINK_NOT_APPLICABLE");
        if (wecom) when(wecomDeliveryService.deliver(eq(row), any())).thenReturn(skipped);
        else when(eventProcessor.processConfirmed(any())).thenReturn(skipped);
        service.deliverDue();
        assertEquals("skipped", row.getStatus());
        assertEquals("LEAD_SOURCE_LINK_NOT_APPLICABLE", row.getLastError());
        assertEquals(2, row.getAttemptCount()); assertNull(row.getLeaseUntil()); assertNull(row.getClaimToken());
        assertNotNull(row.getSucceededAt());
    }

    @Test
    void permanentFailureIsNotRetriedAndUpdateUsesClaimToken() {
        NotifyBusinessOutboxDO row = row(1L, "event:1");
        row.setPayload("null");
        when(outboxMapper.selectDue(any(), eq(100))).thenReturn(List.of(row));
        when(outboxMapper.claim(eq(1L), any(), any(), anyString())).thenReturn(1);
        when(outboxMapper.selectClaimed(eq(1L), anyString())).thenAnswer(call -> { row.setClaimToken(call.getArgument(1)); return row; });
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
        when(outboxMapper.selectClaimed(anyLong(), anyString())).thenAnswer(call -> {
            var claimed = Long.valueOf(1L).equals(call.getArgument(0)) ? first : second;
            claimed.setClaimToken(call.getArgument(1)); return claimed;
        });
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
        when(outboxMapper.selectClaimed(eq(1L), anyString())).thenAnswer(call -> { row.setClaimToken(call.getArgument(1)); return row; });
        when(eventProcessor.processConfirmed(any())).thenReturn(
                NotifySendResult.failure("TEMPORARY", "recipient unavailable", true));

        service.deliverDue();

        assertEquals("pending", row.getStatus());
        assertEquals(1, row.getAttemptCount());
        assertNotNull(row.getNextAttemptAt());
    }

    @Test void claimedSnapshotReplacesStaleScanAndUsesWecomEnvelope() {
        var scanned = row(1L, "event:1");
        var fresh = row(1L, "event:1");
        var envelope = new WecomOutboxPayload();
        envelope.setEventPayload(java.util.Map.of("version", "fresh"));
        fresh.setPayload(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(envelope));
        when(outboxMapper.selectDue(any(), eq(100))).thenReturn(List.of(scanned));
        when(outboxMapper.claim(eq(1L), any(), any(), anyString())).thenReturn(1);
        when(outboxMapper.selectClaimed(eq(1L), anyString())).thenAnswer(call -> {
            fresh.setClaimToken(call.getArgument(1)); return fresh;
        });
        when(wecomDeliveryService.deliver(eq(fresh), any())).thenAnswer(call -> {
            var event = (cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent) call.getArgument(1);
            assertEquals("fresh", event.getPayload().get("version"));
            return NotifySendResult.success("ok");
        });
        service.deliverDue();
        assertEquals("succeeded", fresh.getStatus());
        verifyNoInteractions(eventProcessor);
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
