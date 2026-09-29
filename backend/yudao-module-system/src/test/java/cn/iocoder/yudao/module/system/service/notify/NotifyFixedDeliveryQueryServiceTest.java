package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.*;
import cn.iocoder.yudao.module.system.dal.mysql.notify.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryPageDTO.Status.*;

@ExtendWith(MockitoExtension.class)
class NotifyFixedDeliveryQueryServiceTest {
    @InjectMocks private NotifyFixedDeliveryQueryService service;
    @Mock private NotifyBusinessOutboxMapper outboxes;
    @Mock private NotifyMessageMapper messages;
    private static final String SCENE = "zsjos.calendar.course";
    private final List<NotifyRecipientDTO> people = List.of(NotifyRecipientDTO.admin(1L), NotifyRecipientDTO.admin(2L));
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(10L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void partialInAppFailureRetainsSuccessfullyCommittedMessage() {
        var row = row(1L, "in_app", "failed"); row.setLastError("DELIVERY_FAILED: private-body");
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        var time = LocalDateTime.of(2026, 9, 28, 12, 0);
        var message = new NotifyMessageDO().setId(55L).setUserId(1L).setUserType(2); message.setCreateTime(time);
        when(messages.selectDeliveryEvidence(10L, SCENE, "event", 11L, people)).thenReturn(List.of(message));
        var result = query(); var recipients = result.rules().getFirst().recipients();
        assertEquals(SUCCEEDED, recipients.getFirst().status()); assertEquals(55L, recipients.getFirst().messageId());
        assertEquals(time, recipients.getFirst().completedTime()); assertEquals(FAILED, recipients.getLast().status());
        assertFalse(JsonUtils.toJsonString(result).contains("private-body"));
    }

    @Test void successfulOutboxWithoutMessageEvidenceIsUnknown() {
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row(1L, "in_app", "succeeded")));
        when(messages.selectDeliveryEvidence(10L, SCENE, "event", 11L, people)).thenReturn(List.of());
        assertTrue(query().rules().getFirst().recipients().stream().allMatch(r -> r.status() == UNKNOWN));
    }

    @Test void inAppCheckpointsRetainIndividualSkipAndConfirmedMessageAfterMessageRetention() {
        var row = row(1L, "in_app", "succeeded");
        var payload = JsonUtils.parseObject(row.getPayload(), NotifyOutboxEventCodec.FixedEnvelope.class);
        var success = new NotifyOutboxEventCodec.InAppRecipient(); success.setIdentity(people.getFirst());
        success.setStatus("succeeded"); success.setMessageId(51L); success.setCompletedTime(LocalDateTime.of(2026, 9, 28, 12, 0));
        var skipped = new NotifyOutboxEventCodec.InAppRecipient(); skipped.setIdentity(people.getLast());
        skipped.setStatus("skipped"); skipped.setErrorCode("EMPLOYEE_UNAVAILABLE"); skipped.setCompletedTime(success.getCompletedTime());
        payload.setRecipients(List.of(success, skipped)); row.setPayload(JsonUtils.toJsonString(payload));
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        var result = query().rules().getFirst().recipients();
        assertEquals(SUCCEEDED, result.getFirst().status()); assertEquals(51L, result.getFirst().messageId());
        assertEquals(success.getCompletedTime(), result.getFirst().completedTime());
        assertEquals(SKIPPED, result.getLast().status()); assertEquals("EMPLOYEE_UNAVAILABLE", result.getLast().errorCode());
        assertFalse(result.getLast().retryScheduled());
    }

    @Test void inAppFailureReportsScheduledRetryAndRejectsForeignCheckpoint() {
        var row = row(1L, "in_app", "pending");
        var payload = JsonUtils.parseObject(row.getPayload(), NotifyOutboxEventCodec.FixedEnvelope.class);
        var failed = new NotifyOutboxEventCodec.InAppRecipient(); failed.setIdentity(people.getFirst());
        failed.setStatus("failed"); failed.setErrorCode("NOTIFY_DELIVERY_FAILED");
        var pending = new NotifyOutboxEventCodec.InAppRecipient(); pending.setIdentity(people.getLast());
        payload.setRecipients(List.of(failed, pending)); row.setPayload(JsonUtils.toJsonString(payload));
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        assertTrue(query().rules().getFirst().recipients().getFirst().retryScheduled());
        row.setStatus("failed"); assertFalse(query().rules().getFirst().recipients().getFirst().retryScheduled());
        pending.setIdentity(NotifyRecipientDTO.admin(99L)); row.setPayload(JsonUtils.toJsonString(payload));
        assertEquals("NOTIFY_PAYLOAD_INVALID", query().rules().getFirst().errorCode());
        assertTrue(query().rules().getFirst().recipients().isEmpty());
    }

    @Test void wecomCheckpointSeparatesUncertaintySuccessAndDoesNotLeakPayload() {
        var row = row(1L, "wecom", "failed");
        var payload = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
        var success = checkpoint(1L, "succeeded"); success.setCompletedTime(LocalDateTime.of(2026, 9, 28, 12, 0));
        payload.setRecipients(List.of(success, checkpoint(2L, "uncertain")));
        row.setPayload(JsonUtils.toJsonString(payload));
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        var result = query();
        assertEquals(SUCCEEDED, result.rules().getFirst().recipients().getFirst().status());
        assertEquals(success.getCompletedTime(), result.rules().getFirst().recipients().getFirst().completedTime());
        assertNull(result.rules().getFirst().recipients().getLast().completedTime());
        assertEquals(UNCERTAIN, result.rules().getFirst().recipients().getLast().status());
        assertFalse(result.rules().getFirst().recipients().getLast().retryScheduled());
        assertFalse(JsonUtils.toJsonString(result).contains("private-")); verifyNoInteractions(messages);
    }

    @Test void retriesAreScheduledOnlyWhileOutboxIsPendingAndInFlightRemainsUncertain() {
        var row = row(1L, "wecom", "pending");
        var payload = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
        var failed = checkpoint(1L, "failed"); failed.setRetryable(true);
        payload.setRecipients(List.of(failed, checkpoint(2L, "sending"))); row.setPayload(JsonUtils.toJsonString(payload));
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        var recipients = query().rules().getFirst().recipients();
        assertTrue(recipients.getFirst().retryScheduled()); assertEquals(UNCERTAIN, recipients.getLast().status());
        row.setStatus("failed"); assertFalse(query().rules().getFirst().recipients().getFirst().retryScheduled());
    }

    @Test void allQueuedIsPendingAndRuleSkipPreservesReason() {
        var row = row(1L, "wecom", "pending");
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        assertTrue(query().rules().getFirst().recipients().stream().allMatch(r -> r.status() == PENDING));
        row.setStatus("skipped"); row.setLastError("RULE_NOT_APPLICABLE");
        var recipient = query().rules().getFirst().recipients().getFirst();
        assertEquals(SKIPPED, recipient.status()); assertEquals("RULE_NOT_APPLICABLE", recipient.errorCode());
    }

    @Test void boundedCursorKeepsIndependentRulesAndFiltersUnselectedPeople() {
        when(outboxes.selectEventEvidence(eq(10L), eq(SCENE), eq("event"), eq(0L), eq(2)))
                .thenReturn(List.of(row(5L, "wecom", "pending"), row(6L, "in_app", "pending")));
        var result = service.query(SCENE, "event", List.of(NotifyRecipientDTO.admin(2L), NotifyRecipientDTO.admin(3L)), 0, 1);
        assertTrue(result.hasMore()); assertEquals(5L, result.nextCursor()); assertEquals(1, result.rules().size());
        assertEquals(List.of(2L), result.rules().getFirst().recipients().stream().map(NotifyDeliveryPageDTO.Recipient::userId).toList());
    }

    @Test void wrongTenantAndSceneFailClosed() {
        var row = row(1L, "wecom", "pending"); row.setTenantId(11L);
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        assertThrows(IllegalStateException.class, this::query);
        row.setTenantId(10L); row.setSceneCode("another"); assertThrows(IllegalStateException.class, this::query);
        verifyNoInteractions(messages);
    }

    @Test void malformedOrForeignCheckpointIsUnknownNotSuccessful() {
        var row = row(1L, "wecom", "succeeded");
        var payload = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
        var item = checkpoint(1L, "succeeded"); item.setContext(item.getContext().toBuilder().tenantId(11L).build());
        payload.setRecipients(List.of(item)); row.setPayload(JsonUtils.toJsonString(payload));
        when(outboxes.selectEventEvidence(10L, SCENE, "event", 0, 11)).thenReturn(List.of(row));
        assertEquals("NOTIFY_PAYLOAD_INVALID", query().rules().getFirst().errorCode());
        row.setPayload("private-invalid-json"); assertTrue(query().rules().getFirst().recipients().isEmpty());
    }

    @Test void rejectsUnboundedOrInvalidIdentityQueriesBeforeDal() {
        assertThrows(IllegalArgumentException.class, () -> service.query(SCENE, "event", people, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.query(SCENE, "event", List.of(), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> service.query(SCENE, "event", List.of(NotifyRecipientDTO.partner(1L)), 0, 10));
        TenantContextHolder.clear(); assertThrows(NullPointerException.class, this::query);
        verifyNoInteractions(outboxes, messages);
    }

    private NotifyDeliveryPageDTO query() { return service.query(SCENE, "event", people, 0, 10); }
    private NotifyBusinessOutboxDO row(Long id, String channel, String status) {
        var row = new NotifyBusinessOutboxDO(); row.setId(id); row.setTenantId(10L); row.setSceneCode(SCENE);
        row.setSourceEventKey("event"); row.setTargetRuleId(11L); row.setStatus(status);
        var event = NotifyBusinessEvent.builder().tenantId(10L).sceneCode(SCENE).sourceEventKey("event")
                .recipientMode("FIXED").fixedRecipients(people).payload(Map.of("private-body", "private-variables")).build();
        row.setPayload(NotifyOutboxEventCodec.encode(event, channel)); return row;
    }
    private WecomOutboxPayload.Recipient checkpoint(Long userId, String status) {
        var item = new WecomOutboxPayload.Recipient(); item.setStatus(status);
        item.setContext(NotifyDeliveryContext.builder().tenantId(10L).sceneCode(SCENE).sourceEventKey("event")
                .ruleId(11L).userId(userId).userType(2).content("private-content").wecomClickUrl("private-ticket").build());
        return item;
    }
}
