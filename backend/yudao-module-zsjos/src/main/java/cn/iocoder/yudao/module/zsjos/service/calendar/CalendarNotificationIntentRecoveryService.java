package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyIntentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Recovery performs durable acceptance only. System retains all channel delivery/retry ownership. */
@Service
public class CalendarNotificationIntentRecoveryService {
    @Resource private CalendarNotifyIntentMapper mapper;
    @Resource private CalendarNotifyStateMapper stateMapper;
    @Resource private CalendarNotifySnapshotMapper snapshotMapper;
    @Resource private CourseCalendarEventMapper courseMapper;
    @Resource private ExamScheduleMapper examMapper;
    @Resource private AdminUserApi users;
    @Resource private CalendarNotificationAccess access;
    @Resource private CalendarNotificationAcceptanceService acceptance;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void recover(Long id) {
        var candidate = mapper.selectById(id);
        if (!pending(candidate)) return;
        // Identity fields are immutable. Acquire the same business/state lock order as foreground maintenance.
        if ("COURSE".equals(candidate.getCalendarType())) courseMapper.selectForUpdate(candidate.getCalendarId());
        else if ("EXAM".equals(candidate.getCalendarType())) examMapper.selectForUpdate(candidate.getCalendarId());
        else throw new IllegalStateException("Invalid persisted calendar type");
        var state = stateMapper.selectForUpdate(candidate.getCalendarType(), candidate.getCalendarId());
        var row = mapper.selectForUpdate(id);
        if (!pending(row) || row.getNextAttemptAt().isAfter(LocalDateTime.now())) return;
        if (state == null || !Objects.equals(state.getTenantId(), row.getTenantId())
                || !Objects.equals(state.getCurrentSnapshotId(), row.getSnapshotId())) {
            finish(row, "SUPERSEDED", null, "CONTENT_CHANGED"); return;
        }
        var actor = users.getUser(row.getOperatorUserId());
        if (actor == null || !CommonStatusEnum.ENABLE.getStatus().equals(actor.getStatus())) {
            finish(row, "SKIPPED", null, "OPERATOR_INACTIVE"); return;
        }
        try { access.checkForUser(row.getCalendarType(), "ALL".equals(row.getScope()), row.getOperatorUserId()); }
        catch (ServiceException denied) {
            if (!Objects.equals(denied.getCode(), CALENDAR_NOTIFY_PERMISSION_DENIED.getCode())
                    && !Objects.equals(denied.getCode(), CALENDAR_NOTIFY_ALL_PERMISSION_DENIED.getCode())) throw denied;
            finish(row, "SKIPPED", null, String.valueOf(denied.getCode())); return;
        }
        var snapshot = snapshotMapper.selectById(row.getSnapshotId());
        if (snapshot == null || !Objects.equals(snapshot.getTenantId(), row.getTenantId())
                || !Objects.equals(snapshot.getCalendarId(), row.getCalendarId())
                || !Objects.equals(snapshot.getCalendarType(), row.getCalendarType()))
            throw new IllegalStateException("Invalid persisted calendar snapshot");
        var roster = JsonUtils.parseArray(row.getRecipientsJson(), CalendarNotificationAcceptanceService.Recipient.class);
        var ids = roster.stream().map(CalendarNotificationAcceptanceService.Recipient::userId).collect(Collectors.toSet());
        var enabled = users.getUserList(ids).stream().filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .map(user -> user.getId()).collect(Collectors.toSet());
        // Never resolve ALL again. Missing/deleted/foreign identities remain skipped, without exposing their current data.
        var validated = roster.stream().map(user -> new CalendarNotificationAcceptanceService.Recipient(user.userId(),
                user.nicknameSnapshot(), user.skipReason() != null ? user.skipReason()
                        : enabled.contains(user.userId()) ? null : "EMPLOYEE_UNAVAILABLE")).toList();
        var result = acceptance.accept(new CalendarNotificationAcceptanceService.Command(snapshot, row.getEventType(), row.getScope(),
                validated, Boolean.TRUE.equals(row.getResend()), row.getOperatorUserId(), row.getAcceptanceKey(), row.getRequestHash()));
        finish(row, result.acceptedCount() == 0 ? "SKIPPED" : "SUBMITTED", result.batchId(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void recordFailure(Long id, RuntimeException failure) {
        var row = mapper.selectForUpdate(id);
        // A second worker might have completed acceptance after the failed transaction rolled back.
        if (!pending(row)) return;
        boolean retryable = failure instanceof TransientDataAccessException
                || failure instanceof ServiceException business && Objects.equals(business.getCode(), CALENDAR_NOTIFY_CONFIG_UNAVAILABLE.getCode());
        String code = failure instanceof ServiceException business ? String.valueOf(business.getCode())
                : retryable ? "DATABASE_RETRY" : "ACCEPTANCE_FAILED";
        int attempts = row.getAttemptCount() + 1;
        var next = LocalDateTime.now().plusSeconds(Math.min(900L, 30L << Math.min(attempts - 1, 5)));
        mapper.updatePending(id, row.getTenantId(), retryable ? "PENDING" : "FAILED", null, attempts,
                next, code, retryable ? null : LocalDateTime.now());
    }

    private boolean pending(CalendarNotifyIntentDO row) {
        return row != null && Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())
                && "PENDING".equals(row.getStatus());
    }
    private void finish(CalendarNotifyIntentDO row, String status, Long batchId, String code) {
        mapper.updatePending(row.getId(), row.getTenantId(), status, batchId, row.getAttemptCount() + 1,
                row.getNextAttemptAt(), code, LocalDateTime.now());
    }
}
