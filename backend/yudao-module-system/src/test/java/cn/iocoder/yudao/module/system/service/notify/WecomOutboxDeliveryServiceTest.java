package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.*;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WecomOutboxDeliveryServiceTest {
    @InjectMocks private WecomOutboxDeliveryService service;
    @Mock private NotifyBusinessEventProcessor processor;
    @Mock private NotifyRuleService ruleService;
    @Mock private NotifyBusinessOutboxMapper outboxMapper;
    @Mock private WecomNotifyChannelAdapter adapter;
    private NotifyBusinessOutboxDO row;
    private NotifyBusinessEvent event;

    @BeforeEach void setup() {
        row = new NotifyBusinessOutboxDO(); row.setId(1L); row.setTenantId(9L); row.setTargetRuleId(2L);
        row.setClaimToken("claim"); row.setPayload(JsonUtils.toJsonString(new WecomOutboxPayload()));
        event = NotifyBusinessEvent.builder().tenantId(9L).targetRuleId(2L).sceneCode("scene").build();
        lenient().when(ruleService.getEnabledRules("scene")).thenAnswer(call -> {
            assertEquals(9L, TenantContextHolder.getTenantId());
            return List.of(NotifyRuleDO.builder().id(2L).channelCode("wecom").build());
        });
        lenient().when(adapter.prepare(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(outboxMapper.checkpoint(eq(1L), eq(9L), eq("claim"), anyString(), any(), any())).thenReturn(1);
    }

    @Test void retrySkipsSuccessAndKeepsTypedSameIdRecipientsAndRenderedSnapshot() {
        prepareTwo();
        when(adapter.send(any())).thenReturn(NotifySendResult.success("one"),
                NotifySendResult.failure("WECOM_API_-1", "busy", true), NotifySendResult.success("two"));
        assertTrue(service.deliver(row, event).isRetryable());
        assertTrue(service.deliver(row, event).isSuccess());
        ArgumentCaptor<NotifyDeliveryContext> sent = ArgumentCaptor.forClass(NotifyDeliveryContext.class);
        verify(adapter, times(3)).send(sent.capture());
        assertEquals(List.of(2, 3, 3), sent.getAllValues().stream().map(NotifyDeliveryContext::getUserType).toList());
        assertEquals("frozen", sent.getAllValues().get(2).getContent());
        verify(processor).prepareWecom(event);
        assertNull(TenantContextHolder.getTenantId());
        var state = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
        assertEquals(List.of("succeeded", "succeeded"), state.getRecipients().stream().map(WecomOutboxPayload.Recipient::getStatus).toList());
    }

    @Test void permanentFailureDoesNotPreventOtherRecipientsFromReceiving() {
        prepareTwo();
        when(adapter.send(any())).thenReturn(NotifySendResult.failure("WECOM_RECIPIENT_INVALID", "invalid", false),
                NotifySendResult.success("ok"));
        assertFalse(service.deliver(row, event).isSuccess());
        assertFalse(service.deliver(row, event).isRetryable());
        verify(adapter, times(2)).send(any());
    }

    @Test void interruptedSendBecomesUncertainWithoutResending() {
        var state = new WecomOutboxPayload(); var recipient = new WecomOutboxPayload.Recipient();
        recipient.setContext(context(3)); recipient.setStatus("sending"); state.setRecipients(List.of(recipient));
        row.setPayload(JsonUtils.toJsonString(state));
        var result = service.deliver(row, event);
        assertFalse(result.isSuccess()); assertFalse(result.isRetryable());
        verifyNoInteractions(adapter, processor);
        assertEquals("uncertain", JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class).getRecipients().get(0).getStatus());
    }

    @Test void lostFenceNeverStartsNetworkSend() {
        prepareTwo();
        when(outboxMapper.checkpoint(any(), any(), any(), any(), any(), any())).thenReturn(1, 0);
        assertThrows(CannotAcquireLockException.class, () -> service.deliver(row, event));
        verify(adapter, never()).send(any());
    }

    @Test void disabledRuleDoesNotUseFrozenRecipients() {
        doReturn(List.of()).when(ruleService).getEnabledRules("scene");
        assertFalse(service.deliver(row, event).isRetryable());
        verifyNoInteractions(adapter, processor, outboxMapper);
    }

    @Test void optedOutRecipientsArePersistedAsSkipped() {
        prepareTwo(); when(adapter.send(any())).thenReturn(NotifySendResult.success("WECOM_RECIPIENT_SKIPPED"));
        assertTrue(service.deliver(row, event).isSuccess());
        var state = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
        assertTrue(state.getRecipients().stream().allMatch(item -> "skipped".equals(item.getStatus())));
    }

    private void prepareTwo() {
        when(processor.prepareWecom(event)).thenReturn(new NotifyBusinessEventProcessor.PreparedWecom(
                List.of(context(2), context(3)), null));
    }
    private NotifyDeliveryContext context(int userType) {
        return NotifyDeliveryContext.builder().tenantId(9L).ruleId(2L).userId(10L).userType(userType)
                .content("frozen").wecomClickPrepared(true).wecomClickUrl("https://example.test/wecom/click?ticket=synthetic").build();
    }
}
