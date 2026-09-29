package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseCalendarSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import cn.iocoder.yudao.module.zsjos.service.coursecalendar.CourseCalendarEventService;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationAcceptanceService.Recipient;

/** Orchestrates existing calendar commands; durable intent creation is part of their business transaction. */
@Service
public class CalendarMaintenanceNotificationService {
    @Resource private CourseCalendarEventService courses;
    @Resource private ExamScheduleService exams;
    @Resource private CourseCalendarEventMapper courseMapper;
    @Resource private ExamScheduleMapper examMapper;
    @Resource private CalendarNotifyStateMapper states;
    @Resource private CalendarNotifySnapshotMapper snapshots;
    @Resource private CalendarNotifyPreviewMapper previews;
    @Resource private CalendarNotifyRecipientMapper recipients;
    @Resource private CalendarNotifyIntentMapper intentMapper;
    @Resource private CalendarNotificationIntentService intents;
    @Resource private CalendarNotificationAccess access;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private PlatformTransactionManager transactionManager;
    @Resource private CalendarNotificationObjectAccess objectAccess;

    public CalendarNotifyPreviewRespVO preview(CalendarNotifyReqVO req) {
        String type = req.getCalendarType(); String event = req.getMaintenanceAction();
        requireManage(type); validateAction(type, event, req.getCalendarId(), req.getCourseContent());
        checkObject(type, req.getCalendarId());
        access.check(type, "ALL".equals(req.getScope()));
        var projected = project(type, event, req.getCalendarId(), req.getCourseContent());
        var options = new CalendarMaintenanceNotifyReqVO(); options.setScope(req.getScope());
        options.setOriginalRecipients(Boolean.TRUE.equals(req.getOriginalRecipients())); options.setUserIds(req.getUserIds());
        options.setResend(Boolean.TRUE.equals(req.getResend())); options.setExpectedVersion(projected.expectedVersion());
        validateOptions(options, false, req.getCalendarId());
        var roster = roster(type, req.getCalendarId(), options);
        Set<Long> accepted = req.getCalendarId() == null ? Set.of() : new HashSet<>(recipients.selectAcceptedUserIds(
                TenantContextHolder.getRequiredTenantId(), type, req.getCalendarId(), projected.snapshot().getCalendarVersion()));
        int invalid = (int) roster.stream().filter(user -> user.skipReason() != null).count();
        int notified = (int) roster.stream().filter(user -> user.skipReason() == null && accepted.contains(user.userId())).count();
        String token = UUID.randomUUID().toString();
        String content = contentHash(projected.snapshot());
        previews.insert(new CalendarNotifyPreviewDO().setTokenHash(hashToken(token)).setOperatorUserId(getLoginUserId())
                .setRequestHash(requestHash(type, event, req.getCalendarId(), req.getCourseContent(), options))
                .setContentHash(content).setRosterHash(rosterHash(roster)).setExpiresAt(LocalDateTime.now().plusMinutes(10)));
        return new CalendarNotifyPreviewRespVO(projected.snapshot().getCalendarVersion(), projected.snapshot().getTitleSnapshot(),
                projected.snapshot().getTimeSnapshot(), projected.snapshot().getRemarkSnapshot(), roster.size(), notified,
                roster.size() - invalid - notified, token, content, projected.expectedVersion(), event, invalid);
    }

    /** The transaction has ended before a competing operation's result is re-read, including duplicate inserts. */
    public Long execute(String type, String event, Long id, CourseCalendarSaveReqVO course, CalendarMaintenanceNotifyReqVO options) {
        requireManage(type); validateAction(type, event, id, course); validateOptions(options, true, id);
        String requestHash = requestHash(type, event, id, course, options);
        var previous = intents.findOperation(options.getOperationKey(), requestHash, getLoginUserId());
        if (previous != null) return previous.getCalendarId();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            // A competing committed deletion can remove the object after the first replay lookup.
            checkObject(type, id);
            return transaction.execute(status -> {
                // Lock the business row before querying state or invoking the existing domain command.
                int currentVersion = lockVersion(type, id);
                var committed = intents.findOperation(options.getOperationKey(), requestHash, getLoginUserId());
                if (committed != null) return committed.getCalendarId();
                if (currentVersion != options.getExpectedVersion()) throw exception(CALENDAR_NOTIFY_VERSION_CONFLICT);
                boolean notify = options.isSend() && hasNotify(type, options.getScope());
                List<Recipient> roster = notify ? roster(type, id, options) : List.of();
                CalendarNotifyPreviewDO credential = notify ? credential(options.getPreviewToken(), requestHash, roster) : null;
                Long resultId = mutate(type, event, id, course);
                var state = states.selectForUpdate(type, resultId);
                if (state == null) throw exception(CALENDAR_NOTIFY_SNAPSHOT_CONFLICT);
                var snapshot = snapshots.selectById(state.getCurrentSnapshotId());
                if (snapshot == null || !Objects.equals(snapshot.getTenantId(), TenantContextHolder.getRequiredTenantId())
                        || !Objects.equals(snapshot.getCalendarId(), resultId) || !Objects.equals(snapshot.getCalendarType(), type))
                    throw exception(CALENDAR_NOTIFY_SNAPSHOT_CONFLICT);
                // IDs of newly created courses are unknown at preview; compare normalized server content after saving.
                if (credential != null && !Objects.equals(credential.getContentHash(), contentHash(snapshot)))
                    throw exception(CALENDAR_NOTIFY_VERSION_CONFLICT);
                intents.record(new CalendarNotificationIntentService.Command(options.getOperationKey(), requestHash, snapshot,
                        event, options.getScope(), roster, options.isResend(), getLoginUserId(), options.isSend()));
                return resultId;
            });
        } catch (RuntimeException failure) {
            // A concurrent duplicate create may lose at the unique intent key; its business writes have rolled back.
            var committed = intents.findOperation(options.getOperationKey(), requestHash, getLoginUserId());
            if (committed != null) return committed.getCalendarId();
            throw failure;
        }
    }

    public CalendarMaintenanceResultRespVO getResult(String key) {
        var row = intentMapper.selectOperation(key);
        if (row == null || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(row.getOperatorUserId(), getLoginUserId())) throw exception(CALENDAR_NOTIFY_OPERATION_NOT_EXISTS);
        requireManage(row.getCalendarType());
        return new CalendarMaintenanceResultRespVO(row.getCalendarId(), row.getCalendarType(), row.getEventType(),
                row.getStatus(), row.getLastErrorCode(), row.getBatchId());
    }

    private CalendarNotifyPreviewDO credential(String token, String requestHash, List<Recipient> roster) {
        if (token == null || token.isBlank() || token.length() > 128) throw exception(CALENDAR_NOTIFY_PREVIEW_INVALID);
        var row = previews.selectToken(hashToken(token));
        if (row == null || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(row.getOperatorUserId(), getLoginUserId()) || !Objects.equals(row.getRequestHash(), requestHash)
                || !row.getExpiresAt().isAfter(LocalDateTime.now())) throw exception(CALENDAR_NOTIFY_PREVIEW_INVALID);
        if (!Objects.equals(row.getRosterHash(), rosterHash(roster))) throw exception(CALENDAR_NOTIFY_ROSTER_CHANGED);
        return row;
    }

    private int lockVersion(String type, Long id) {
        if (id == null) return 0;
        if ("COURSE".equals(type)) {
            var row = courseMapper.selectForUpdate(id);
            if (row == null) throw exception(COURSE_CALENDAR_NOT_EXISTS);
            return row.getCalendarVersion() == null ? 1 : row.getCalendarVersion();
        }
        var row = examMapper.selectForUpdate(id);
        if (row == null) throw exception(EXAM_SCHEDULE_NOT_EXISTS);
        return row.getCalendarVersion() == null ? 1 : row.getCalendarVersion();
    }

    private Long mutate(String type, String event, Long id, CourseCalendarSaveReqVO course) {
        if ("COURSE".equals(type)) {
            if ("CREATED".equals(event)) return courses.create(course);
            if ("UPDATED".equals(event)) courses.update(id, course); else courses.delete(id);
        } else if ("PUBLISHED".equals(event)) exams.publish(id, getLoginUserId());
        else exams.revoke(id, getLoginUserId());
        return id;
    }

    private record Projection(CalendarNotifySnapshotDO snapshot, int expectedVersion) {}
    private Projection project(String type, String event, Long id, CourseCalendarSaveReqVO course) {
        if ("EXAM".equals(type)) {
            var row = exams.previewTransition(id, event, getLoginUserId());
            return new Projection(CalendarNotificationSnapshotService.projectExam(row, event), row.getCalendarVersion() - 1);
        }
        var current = id == null ? null : courseMapper.selectById(id);
        if (id != null && current == null) throw exception(COURSE_CALENDAR_NOT_EXISTS);
        int version = current == null ? 0 : current.getCalendarVersion() == null ? 1 : current.getCalendarVersion();
        var row = "DELETED".equals(event) ? BeanUtils.toBean(current, CourseCalendarEventDO.class).setCalendarVersion(version + 1)
                : courses.previewSave(id, course);
        return new Projection(CalendarNotificationSnapshotService.projectCourse(row, event, "DELETED".equals(event) ? "DELETED" : "ACTIVE"), version);
    }

    private List<Recipient> roster(String type, Long id, CalendarMaintenanceNotifyReqVO options) {
        if (options.isOriginalRecipients()) {
            Map<Long, Recipient> historical = new LinkedHashMap<>();
            recipients.selectHistoricalRecipients(TenantContextHolder.getRequiredTenantId(), type, id).forEach(row ->
                    historical.putIfAbsent(row.getUserId(), new Recipient(row.getUserId(), row.getNicknameSnapshot(), null)));
            if (historical.isEmpty()) return List.of();
            var active = users.getUserList(historical.keySet()).stream().filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                    .map(user -> user.getId()).collect(Collectors.toSet());
            return historical.values().stream().sorted(Comparator.comparing(Recipient::userId)).map(user ->
                    new Recipient(user.userId(), user.nicknameSnapshot(), active.contains(user.userId()) ? null : "EMPLOYEE_UNAVAILABLE")).toList();
        }
        var requested = options.getUserIds() == null ? Set.<Long>of() : new HashSet<>(options.getUserIds());
        if ("SPECIFIED".equals(options.getScope()) && requested.isEmpty()) throw exception(CALENDAR_NOTIFY_RECIPIENT_EMPTY);
        var selected = "ALL".equals(options.getScope()) ? users.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus()) : users.getUserList(requested);
        if (selected.isEmpty()) throw exception(CALENDAR_NOTIFY_RECIPIENT_EMPTY);
        if (selected.stream().anyMatch(user -> !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                || "SPECIFIED".equals(options.getScope()) && !requested.equals(selected.stream().map(user -> user.getId()).collect(Collectors.toSet())))
            throw exception(CALENDAR_NOTIFY_RECIPIENT_INVALID);
        return selected.stream().sorted(Comparator.comparing(user -> user.getId())).map(user -> new Recipient(user.getId(), user.getNickname(), null)).toList();
    }

    private void validateAction(String type, String event, Long id, CourseCalendarSaveReqVO course) {
        if (event == null || !("COURSE".equals(type) ? Set.of("CREATED", "UPDATED", "DELETED") : Set.of("PUBLISHED", "REVOKED")).contains(event)
                || "CREATED".equals(event) && id != null || !"CREATED".equals(event) && (id == null || id <= 0)
                || "COURSE".equals(type) && !"DELETED".equals(event) && course == null) throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
    }
    private void validateOptions(CalendarMaintenanceNotifyReqVO options, boolean execute, Long id) {
        if (options == null || options.getExpectedVersion() == null || options.getExpectedVersion() < 0
                || !("SPECIFIED".equals(options.getScope()) || "ALL".equals(options.getScope()))
                || execute && (options.getOperationKey() == null || options.getOperationKey().isBlank() || options.getOperationKey().length() > 128)
                || options.isOriginalRecipients() && (id == null || !"SPECIFIED".equals(options.getScope())
                    || options.getUserIds() != null && !options.getUserIds().isEmpty())) throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
        if (options.getUserIds() != null && options.getUserIds().stream().anyMatch(userId -> userId == null || userId <= 0))
            throw exception(CALENDAR_NOTIFY_RECIPIENT_INVALID);
    }
    private void checkObject(String type, Long id) {
        if (id == null) return;
        if ("COURSE".equals(type)) objectAccess.courseMaintenance(id); else objectAccess.examMaintenance(id);
    }
    private void requireManage(String type) {
        if (!("COURSE".equals(type) || "EXAM".equals(type))) throw exception(CALENDAR_NOTIFY_TYPE_INVALID);
        TenantContextHolder.getRequiredTenantId();
        if (getLoginUserId() == null || !permissions.hasAnyPermissions(getLoginUserId(), "EXAM".equals(type)
                ? "zsjos:exam-calendar:manage" : "zsjos:course-calendar:manage")) throw exception(CALENDAR_NOTIFY_MANAGE_PERMISSION_DENIED);
    }
    private boolean hasNotify(String type, String scope) {
        try { return access.check(type, "ALL".equals(scope)); }
        catch (ServiceException denied) {
            if (Objects.equals(denied.getCode(), CALENDAR_NOTIFY_PERMISSION_DENIED.getCode())
                    || Objects.equals(denied.getCode(), CALENDAR_NOTIFY_ALL_PERMISSION_DENIED.getCode())) return false;
            throw denied;
        }
    }
    private String requestHash(String type, String event, Long id, CourseCalendarSaveReqVO course, CalendarMaintenanceNotifyReqVO options) {
        Object content = course == null ? null : Arrays.asList(course.getCourseName().trim(), course.getCourseFormValue(), course.getStartTime(),
                course.getEndTime(), course.getRemark(), course.getAttachmentIds() == null ? List.of() : course.getAttachmentIds().stream().distinct().sorted().toList());
        Object selected = options.getUserIds() == null || "ALL".equals(options.getScope()) ? List.of() : options.getUserIds().stream().distinct().sorted().toList();
        return hash(Arrays.asList("MAINTENANCE", getLoginUserId(), type, event, id, options.getExpectedVersion(), content,
                options.isSend(), options.getScope(), options.isOriginalRecipients(), selected, options.isResend()));
    }
    private String contentHash(CalendarNotifySnapshotDO snapshot) {
        return hash(Arrays.asList(snapshot.getCalendarType(), snapshot.getCalendarVersion(), snapshot.getRecordStatus(),
                snapshot.getTitleSnapshot(), snapshot.getTimeSnapshot(), snapshot.getRemarkSnapshot(), snapshot.getDetailsJson()));
    }
    private String rosterHash(List<Recipient> roster) { return hash(roster.stream().map(user -> Arrays.asList(user.userId(), user.skipReason())).toList()); }
    private String hash(Object value) { return DigestUtil.sha256Hex(JsonUtils.toJsonString(value)); }
    private String hashToken(String token) { return DigestUtil.sha256Hex(token); }
}
