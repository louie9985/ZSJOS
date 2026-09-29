package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifySendRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Internal acceptance boundary. Caller validates authority/preview in READ_COMMITTED, locking calendar before state. */
@Service
public class CalendarNotificationAcceptanceService {
    @Resource private CalendarNotifyBatchMapper batchMapper;
    @Resource private CalendarNotifyRecipientMapper recipientMapper;
    @Resource private CalendarNotifyStateMapper stateMapper;
    @Resource private NotifyBusinessEventApi notifyApi;

    /** Fixed roster from a validated request or persisted intent; skipReason retains invalid historical identities. */
    public record Recipient(Long userId, String nicknameSnapshot, String skipReason) {}

    public record Command(CalendarNotifySnapshotDO snapshot, String eventType, String scope,
                          List<Recipient> recipients, boolean resend, Long operatorUserId,
                          String idempotencyKey, String requestHash) {}

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CalendarNotifySendRespVO accept(Command command) {
        if (command == null) throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
        var snapshot = command.snapshot();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (snapshot == null || snapshot.getId() == null || !Objects.equals(tenantId, snapshot.getTenantId())
                || snapshot.getCalendarId() == null || snapshot.getCalendarVersion() == null || snapshot.getCalendarVersion() < 1
                || command.operatorUserId() == null || command.operatorUserId() <= 0 || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128 || command.requestHash() == null || command.requestHash().isBlank()
                || !("ALL".equals(command.scope()) || "SPECIFIED".equals(command.scope()))
                || snapshot.getCalendarType() == null || !Set.of("EXAM", "COURSE").contains(snapshot.getCalendarType())
                || command.eventType() == null || !Set.of("PUBLISHED", "CREATED", "UPDATED", "REVOKED", "DELETED", "MANUAL").contains(command.eventType()))
            throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
        var previous = findPrevious(command.idempotencyKey(), command.requestHash(), command.operatorUserId());
        if (previous != null) return previous;
        var state = stateMapper.selectForUpdate(snapshot.getCalendarType(), snapshot.getCalendarId());
        previous = findPrevious(command.idempotencyKey(), command.requestHash(), command.operatorUserId());
        if (previous != null) return previous;
        if (state == null || !Objects.equals(state.getTenantId(), tenantId)
                || !Objects.equals(state.getCurrentSnapshotId(), snapshot.getId())
                || !Objects.equals(state.getCalendarVersion(), snapshot.getCalendarVersion())
                || !Objects.equals(state.getRecordStatus(), snapshot.getRecordStatus())) throw exception(CALENDAR_NOTIFY_VERSION_CONFLICT);
        boolean active = "COURSE".equals(snapshot.getCalendarType()) ? "ACTIVE".equals(snapshot.getRecordStatus())
                : "PUBLISHED".equals(snapshot.getRecordStatus());
        boolean terminal = "COURSE".equals(snapshot.getCalendarType())
                ? "DELETED".equals(snapshot.getRecordStatus()) && "DELETED".equals(command.eventType())
                : "REVOKED".equals(snapshot.getRecordStatus()) && "REVOKED".equals(command.eventType());
        if ((!active && !terminal) || (active && Set.of("DELETED", "REVOKED").contains(command.eventType())))
            throw exception(CALENDAR_NOTIFY_STATE_INVALID);
        var roster = command.recipients();
        if (roster == null || roster.isEmpty()) throw exception(CALENDAR_NOTIFY_RECIPIENT_EMPTY);
        Set<Long> identities = new HashSet<>();
        for (var recipient : roster) {
            if (recipient == null || recipient.userId() == null || recipient.userId() <= 0 || !identities.add(recipient.userId()))
                throw exception(CALENDAR_NOTIFY_RECIPIENT_INVALID);
        }
        var notified = new HashSet<>(recipientMapper.selectAcceptedUserIds(tenantId, snapshot.getCalendarType(),
                snapshot.getCalendarId(), snapshot.getCalendarVersion()));
        var selected = roster.stream().filter(user -> user.skipReason() == null
                && (command.resend() || !notified.contains(user.userId()))).toList();
        var selectedIds = selected.stream().map(Recipient::userId).collect(Collectors.toSet());
        String eventKey = "calendar:" + UUID.randomUUID();
        var batch = new CalendarNotifyBatchDO().setCalendarType(snapshot.getCalendarType()).setCalendarId(snapshot.getCalendarId())
                .setCalendarVersion(snapshot.getCalendarVersion()).setSnapshotId(snapshot.getId()).setEventType(command.eventType())
                .setScope(command.scope()).setTitleSnapshot(snapshot.getTitleSnapshot()).setTimeSnapshot(snapshot.getTimeSnapshot())
                .setRemarkSnapshot(snapshot.getRemarkSnapshot()).setSourceEventKey(eventKey).setResend(command.resend())
                .setStatus(selected.isEmpty() ? "SKIPPED" : "SUBMITTED").setIdempotencyKey(command.idempotencyKey())
                .setRequestHash(command.requestHash()).setOperatorUserId(command.operatorUserId())
                .setRequestedCount(roster.size()).setAcceptedCount(selected.size()).setSkippedCount(roster.size() - selected.size());
        batch.setTenantId(tenantId);
        try { batchMapper.insert(batch); }
        catch (DuplicateKeyException duplicate) { throw exception(CALENDAR_NOTIFY_IDEMPOTENCY_CONFLICT); }
        for (var user : roster) {
            boolean accepted = selectedIds.contains(user.userId());
            String reason = accepted ? null : user.skipReason() == null ? "ALREADY_ACCEPTED" : user.skipReason();
            var recipient = new CalendarNotifyRecipientDO().setBatchId(batch.getId()).setUserId(user.userId())
                    .setUserType(UserTypeEnum.ADMIN.getValue()).setNicknameSnapshot(user.nicknameSnapshot())
                    .setAccepted(accepted).setStatus(accepted ? "PENDING" : "SKIPPED").setSkipReason(reason)
                    .setCompletedTime(accepted ? null : LocalDateTime.now());
            recipient.setTenantId(tenantId);
            // Reminders do not compete for the ordinary-send key, but still count as accepted in later previews.
            if (accepted && !command.resend()) recipient.setDedupKey(DigestUtil.sha256Hex(JsonUtils.toJsonString(
                    Arrays.asList(snapshot.getCalendarType(), snapshot.getCalendarId(), snapshot.getCalendarVersion(),
                            UserTypeEnum.ADMIN.getValue(), user.userId()))));
            recipientMapper.insert(recipient);
        }
        if (!selected.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("calendar.title", snapshot.getTitleSnapshot());
            payload.put("calendar.time", snapshot.getTimeSnapshot());
            payload.put("calendar.remark", snapshot.getRemarkSnapshot());
            payload.put("calendar.type", snapshot.getCalendarType());
            payload.put("calendar.eventType", command.eventType());
            int rules = notifyApi.publishDurable(NotifyBusinessEvent.builder().tenantId(tenantId)
                    .sceneCode("EXAM".equals(snapshot.getCalendarType()) ? CalendarNotificationSceneProvider.EXAM : CalendarNotificationSceneProvider.COURSE)
                    .sourceEventKey(eventKey).bizType("calendar").bizId(snapshot.getCalendarId()).operatorUserId(command.operatorUserId())
                    .occurredAt(LocalDateTime.now()).recipientMode("FIXED")
                    .fixedRecipients(selected.stream().map(user -> NotifyRecipientDTO.admin(user.userId())).toList()).payload(payload).build());
            if (rules == 0) throw exception(CALENDAR_NOTIFY_CONFIG_UNAVAILABLE);
        }
        return result(batch);
    }

    public CalendarNotifySendRespVO findPrevious(String key, String requestHash, Long operatorUserId) {
        var previous = batchMapper.selectIdempotencyKey(key);
        if (previous == null) return null;
        if (!Objects.equals(previous.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(previous.getOperatorUserId(), operatorUserId)
                || !Objects.equals(previous.getRequestHash(), requestHash)) throw exception(CALENDAR_NOTIFY_IDEMPOTENCY_CONFLICT);
        return result(previous);
    }

    private CalendarNotifySendRespVO result(CalendarNotifyBatchDO batch) {
        return new CalendarNotifySendRespVO(batch.getId(), batch.getAcceptedCount(), Boolean.TRUE.equals(batch.getResend()),
                batch.getSourceEventKey(), batch.getSkippedCount(), batch.getStatus());
    }
}
