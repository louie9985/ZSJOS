package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyBusinessOutboxMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyMessageMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixedInAppOutboxDeliveryServiceTest {
    @InjectMocks private FixedInAppOutboxDeliveryService service;
    @Mock private NotifyBusinessEventProcessor processor;
    @Mock private NotifyBusinessMessageCreator creator;
    @Mock private NotifyBusinessOutboxMapper outboxes;
    @Mock private NotifyMessageMapper messages;
    @Mock private AdminUserMapper users;

    private final NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(10L)
            .sceneCode("zsjos.calendar.course").sourceEventKey("fixed-test").targetRuleId(11L)
            .recipientMode("FIXED").fixedRecipients(List.of(NotifyRecipientDTO.admin(1L), NotifyRecipientDTO.admin(2L)))
            .payload(Map.of("calendar.title", "测试课程")).build();

    @Test void committedMessageIsRecoveredBeforeEmployeeLookupAndRemainingEmployeeIsSkipped() {
        var row = row(); ready();
        when(messages.selectDeliveryEvidence(eq(10L), anyString(), anyString(), eq(11L), eq(List.of(NotifyRecipientDTO.admin(1L)))))
                .thenReturn(List.of(message(1L)));
        var result = service.deliver(row, event);
        assertTrue(result.isSuccess());
        var people = state(row).getRecipients();
        assertEquals("succeeded", people.getFirst().getStatus());
        assertEquals(101L, people.getFirst().getMessageId());
        assertEquals(message(1L).getCreateTime(), people.getFirst().getCompletedTime());
        assertEquals("skipped", people.getLast().getStatus());
        assertEquals("EMPLOYEE_UNAVAILABLE", people.getLast().getErrorCode());
        assertNotNull(people.getLast().getCompletedTime());
        verify(users, never()).selectById(1L); verifyNoInteractions(creator);
        service.deliver(row, event);
        verify(users, times(1)).selectById(2L); // A re-enabled employee is not silently re-sent this batch.
    }

    @Test void oneFailureDoesNotPreventOtherRecipientAndRetryOnlyRevisitsFailure() {
        var row = row(); ready(); enabled(1L); enabled(2L);
        when(messages.selectDeliveryEvidence(eq(10L), anyString(), anyString(), eq(11L), eq(List.of(NotifyRecipientDTO.admin(1L)))))
                .thenReturn(List.of());
        doNothing().when(creator).create(eq(event), any(), any(), any(), eq(NotifyRecipientDTO.admin(2L)));
        doThrow(new IllegalStateException("private failure")).doNothing()
                .when(creator).create(eq(event), any(), any(), any(), eq(NotifyRecipientDTO.admin(1L)));
        when(messages.selectDeliveryEvidence(eq(10L), anyString(), anyString(), eq(11L), eq(List.of(NotifyRecipientDTO.admin(2L)))))
                .thenReturn(List.of(), List.of(message(2L)));
        assertTrue(service.deliver(row, event).isRetryable());
        assertEquals("failed", state(row).getRecipients().getFirst().getStatus());
        assertEquals("succeeded", state(row).getRecipients().getLast().getStatus());
        assertFalse(row.getPayload().contains("private failure"));
        when(messages.selectDeliveryEvidence(eq(10L), anyString(), anyString(), eq(11L), eq(List.of(NotifyRecipientDTO.admin(1L)))))
                .thenReturn(List.of(), List.of(message(1L)));
        assertTrue(service.deliver(row, event).isSuccess());
        verify(creator, times(1)).create(eq(event), any(), any(), any(), eq(NotifyRecipientDTO.admin(2L)));
        verify(creator, times(2)).create(eq(event), any(), any(), any(), eq(NotifyRecipientDTO.admin(1L)));
    }

    @Test void missingDisabledDeletedOrForeignEmployeeNeverCreatesMessage() {
        for (int mode = 0; mode < 4; mode++) {
            reset(processor, outboxes, messages, users, creator);
            var row = row(); ready();
            if (mode > 0) {
                var user = new AdminUserDO().setId(1L).setStatus(mode == 1 ? 1 : 0);
                user.setTenantId(mode == 3 ? 20L : 10L); user.setDeleted(mode == 2);
                when(users.selectById(1L)).thenReturn(user);
            }
            assertTrue(service.deliver(row, event).isSuccess());
            assertTrue(state(row).getRecipients().stream().allMatch(item -> "EMPLOYEE_UNAVAILABLE".equals(item.getErrorCode())));
            verifyNoInteractions(creator);
        }
    }

    @Test void businessSkipAndTenantArePreserved() {
        var row = row(); ready(); enabled(1L); enabled(2L);
        when(processor.deliverySkipReason(event)).thenAnswer(call -> {
            assertEquals(10L, TenantContextHolder.getRequiredTenantId()); return "CONTENT_CHANGED";
        });
        assertTrue(service.deliver(row, event).isSuccess());
        assertTrue(state(row).getRecipients().stream().allMatch(item -> "CONTENT_CHANGED".equals(item.getErrorCode())));
        verifyNoInteractions(creator);
    }

    @Test void unrelatedDuplicateKeyIsNotSuccessWithoutPersistedMessage() {
        var row = row(); ready(); enabled(1L);
        doThrow(new DuplicateKeyException("other index")).when(creator).create(any(), any(), any(), any(), any());
        assertFalse(service.deliver(row, event).isSuccess());
        assertEquals("NOTIFY_RECIPIENT_EVIDENCE_MISSING", state(row).getRecipients().getFirst().getErrorCode());
        assertNull(state(row).getRecipients().getFirst().getCompletedTime());
    }

    @Test void initialLeaseLossStopsBeforeAnyEmployeeOrMessageAccess() {
        var row = row();
        when(processor.prepareFixedInApp(event)).thenReturn(new NotifyBusinessEventProcessor.PreparedInApp(null, null, null, null));
        assertThrows(CannotAcquireLockException.class, () -> service.deliver(row, event));
        verifyNoInteractions(users, messages, creator);
    }

    @Test void ruleFailureDoesNotCreateOrCheckpointMessages() {
        when(processor.prepareFixedInApp(event)).thenReturn(new NotifyBusinessEventProcessor.PreparedInApp(null, null, null,
                NotifySendResult.failure("NOTIFY_RULE_MISSING", "不可用", false)));
        assertEquals("NOTIFY_RULE_MISSING", service.deliver(row(), event).getErrorCode());
        verifyNoInteractions(users, messages, creator, outboxes);
    }

    @Test void malformedRosterCheckpointOrCallerEventIsRejectedBeforeProcessing() {
        var row = row(); var state = state(row);
        var item = new NotifyOutboxEventCodec.InAppRecipient(); item.setIdentity(NotifyRecipientDTO.admin(99L));
        state.setRecipients(List.of(item)); row.setPayload(JsonUtils.toJsonString(state));
        assertThrows(IllegalArgumentException.class, () -> service.deliver(row, event));
        assertThrows(IllegalArgumentException.class, () -> service.deliver(row(), event.toBuilder().tenantId(20L).build()));
        verifyNoInteractions(processor, creator, outboxes, messages, users);
    }

    private void ready() {
        when(processor.prepareFixedInApp(event)).thenReturn(new NotifyBusinessEventProcessor.PreparedInApp(null, null, null, null));
        when(outboxes.checkpoint(anyLong(), eq(10L), eq("claim"), anyString(), any(), any())).thenReturn(1);
    }
    private void enabled(long id) {
        var user = new AdminUserDO().setId(id).setStatus(0); user.setTenantId(10L);
        when(users.selectById(id)).thenReturn(user);
    }
    private NotifyMessageDO message(long id) {
        var message = new NotifyMessageDO().setId(id + 100).setUserId(id).setUserType(2);
        message.setCreateTime(LocalDateTime.of(2026, 9, 28, 12, 0)); return message;
    }
    private NotifyBusinessOutboxDO row() {
        var row = new NotifyBusinessOutboxDO(); row.setId(1L); row.setTenantId(10L);
        row.setSceneCode(event.getSceneCode()); row.setSourceEventKey(event.getSourceEventKey()); row.setTargetRuleId(11L);
        row.setPayload(NotifyOutboxEventCodec.encode(event, "in_app")); row.setClaimToken("claim"); return row;
    }
    private NotifyOutboxEventCodec.FixedEnvelope state(NotifyBusinessOutboxDO row) {
        return JsonUtils.parseObject(row.getPayload(), NotifyOutboxEventCodec.FixedEnvelope.class);
    }
}
