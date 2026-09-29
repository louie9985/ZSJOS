package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifySnapshotDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarNotifyIntentMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Persist only after the maintenance caller validates its preview and holds the calendar/state locks. */
@Service
public class CalendarNotificationIntentService {
    @Resource private CalendarNotifyIntentMapper mapper;
    @Resource private CalendarNotificationAccess access;
    @Resource private ApplicationEventPublisher events;

    public record Ready(Long tenantId, Long intentId) {}
    public record Command(String operationKey, String requestHash, CalendarNotifySnapshotDO snapshot,
                          String eventType, String scope, List<CalendarNotificationAcceptanceService.Recipient> recipients,
                          boolean resend, Long operatorUserId, boolean requested) {
        public Command(String operationKey, String requestHash, CalendarNotifySnapshotDO snapshot, String eventType,
                       String scope, List<CalendarNotificationAcceptanceService.Recipient> recipients, boolean resend, Long operatorUserId) {
            this(operationKey, requestHash, snapshot, eventType, scope, recipients, resend, operatorUserId, true);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CalendarNotifyIntentDO record(Command command) {
        if (command == null || command.snapshot() == null || command.snapshot().getId() == null
                || !Objects.equals(command.snapshot().getTenantId(), TenantContextHolder.getRequiredTenantId())
                || command.operationKey() == null || command.operationKey().isBlank() || command.operationKey().length() > 128
                || command.requestHash() == null || !command.requestHash().matches("[a-f0-9]{64}")
                || command.operatorUserId() == null || command.operatorUserId() <= 0
                || !("ALL".equals(command.scope()) || "SPECIFIED".equals(command.scope()))
                || command.eventType() == null || !Set.of("PUBLISHED", "CREATED", "UPDATED", "REVOKED", "DELETED").contains(command.eventType()))
            throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
        var previous = findOperation(command.operationKey(), command.requestHash(), command.operatorUserId());
        if (previous != null) return previous;
        String skip = command.requested() ? null : "CANCELLED";
        try { if (command.requested()) access.checkForUser(command.snapshot().getCalendarType(), "ALL".equals(command.scope()), command.operatorUserId()); }
        catch (ServiceException denied) {
            if (!Objects.equals(denied.getCode(), CALENDAR_NOTIFY_PERMISSION_DENIED.getCode())
                    && !Objects.equals(denied.getCode(), CALENDAR_NOTIFY_ALL_PERMISSION_DENIED.getCode())) throw denied;
            skip = String.valueOf(denied.getCode());
        }
        var roster = command.recipients() == null ? List.<CalendarNotificationAcceptanceService.Recipient>of() : command.recipients();
        Set<Long> ids = new HashSet<>();
        for (var user : roster) if (user == null || user.userId() == null || user.userId() <= 0 || !ids.add(user.userId()))
            throw exception(CALENDAR_NOTIFY_RECIPIENT_INVALID);
        if (roster.isEmpty() && skip == null) skip = "NO_RECIPIENTS";
        var row = new CalendarNotifyIntentDO().setOperationKey(command.operationKey()).setRequestHash(command.requestHash())
                .setAcceptanceKey("calendar-intent:" + UUID.randomUUID()).setOperatorUserId(command.operatorUserId())
                .setCalendarType(command.snapshot().getCalendarType()).setCalendarId(command.snapshot().getCalendarId())
                .setSnapshotId(command.snapshot().getId()).setEventType(command.eventType()).setScope(command.scope())
                .setRecipientsJson(JsonUtils.toJsonString(roster)).setResend(command.resend()).setStatus(skip == null ? "PENDING" : "SKIPPED")
                .setAttemptCount(0).setNextAttemptAt(LocalDateTime.now()).setLastErrorCode(skip)
                .setCompletedTime(skip == null ? null : LocalDateTime.now());
        row.setTenantId(TenantContextHolder.getRequiredTenantId());
        mapper.insert(row);
        if (skip == null) events.publishEvent(new Ready(row.getTenantId(), row.getId()));
        return row;
    }

    public CalendarNotifyIntentDO findOperation(String key, String hash, Long operatorUserId) {
        var row = mapper.selectOperation(key);
        if (row != null && (!Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(row.getOperatorUserId(), operatorUserId) || !Objects.equals(row.getRequestHash(), hash)))
            throw exception(CALENDAR_NOTIFY_IDEMPOTENCY_CONFLICT);
        return row;
    }
}
