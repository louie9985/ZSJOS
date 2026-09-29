package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyUserPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifySendRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyPreviewRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyBatchRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyBatchPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.CalendarNotifyRecipientRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class CalendarNotificationService {
    @Resource private AdminUserApi adminUserApi;
    @Resource private CalendarNotificationAcceptanceService acceptance;
    @Resource private CalendarNotifyBatchMapper batchMapper;
    @Resource private CalendarNotifyRecipientMapper recipientMapper;
    @Resource private CourseCalendarEventMapper courseMapper;
    @Resource private ExamScheduleMapper examMapper;
    @Resource private CalendarNotificationAccess notificationAccess;
    @Resource private CalendarNotificationSnapshotService snapshots;
    @Resource private CalendarNotifyPreviewMapper previewMapper;
    @Resource private CalendarNotificationObjectAccess objectAccess;

    public PageResult<CalendarNotifyUserRespVO> getUsers(CalendarNotifyUserPageReqVO req) {
        notificationAccess.check(req.getCalendarType(), false);
        var query = new AdminUserCandidatePageReqDTO();
        query.setQualificationMode("ALL");
        query.setKeyword(req.getKeyword());
        query.setPageNo(req.getPageNo());
        query.setPageSize(req.getPageSize());
        var page = adminUserApi.getCandidateUserPage(query);
        return new PageResult<>(page.getList().stream()
                .map(user -> new CalendarNotifyUserRespVO(user.getId(), user.getNickname(), user.getDeptId())).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CalendarNotifySendRespVO send(CalendarNotifyReqVO req) {
        notificationAccess.check(req.getCalendarType(), "ALL".equalsIgnoreCase(req.getScope()));
        validateRequest(req);
        if (req.getCalendarVersion() == null || req.getCalendarVersion() < 1) throw exception(CALENDAR_NOTIFY_VERSION_CONFLICT);
        if (req.getIdempotencyKey() == null || req.getIdempotencyKey().isBlank() || req.getIdempotencyKey().length() > 128)
            throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
        String requestHash = requestHash(req, req.getCalendarVersion());
        // A committed retry must remain readable after deletion/revocation or preview expiry.
        var previous = acceptance.findPrevious(req.getIdempotencyKey(), requestHash, getLoginUserId());
        if (previous != null) return previous;
        // The calendar lock serializes maintenance and acceptance, followed by the snapshot-state lock.
        var snapshot = snapshot(req, true);
        // Another request may have committed while this request waited on the calendar lock.
        previous = acceptance.findPrevious(req.getIdempotencyKey(), requestHash, getLoginUserId());
        if (previous != null) return previous;
        if (!Objects.equals(req.getCalendarVersion(), snapshot.getCalendarVersion())) throw exception(CALENDAR_NOTIFY_VERSION_CONFLICT);
        var users = recipients(req);
        verifyPreview(req, snapshot, requestHash, users);
        return acceptance.accept(new CalendarNotificationAcceptanceService.Command(snapshot, "MANUAL",
                req.getScope().toUpperCase(Locale.ROOT), users.stream().map(user ->
                new CalendarNotificationAcceptanceService.Recipient(user.getId(), user.getNickname(), null)).toList(),
                Boolean.TRUE.equals(req.getResend()), getLoginUserId(), req.getIdempotencyKey(), requestHash));
    }
    public CalendarNotifyPreviewRespVO preview(CalendarNotifyReqVO req) {
        notificationAccess.check(req.getCalendarType(), "ALL".equalsIgnoreCase(req.getScope()));
        validateRequest(req);
        var snapshot = snapshot(req, false);
        var users = recipients(req);
        var accepted = acceptedUsers(snapshot);
        int notified = (int) users.stream().filter(user -> accepted.contains(user.getId())).count();
        String token = UUID.randomUUID().toString();
        var preview = new CalendarNotifyPreviewDO().setTokenHash(DigestUtil.sha256Hex(token)).setOperatorUserId(getLoginUserId())
                .setRequestHash(requestHash(req, snapshot.getCalendarVersion())).setContentHash(snapshot.getContentHash())
                .setRosterHash(rosterHash(users)).setExpiresAt(LocalDateTime.now().plusMinutes(10));
        previewMapper.insert(preview);
        return new CalendarNotifyPreviewRespVO(snapshot.getCalendarVersion(), snapshot.getTitleSnapshot(), snapshot.getTimeSnapshot(), snapshot.getRemarkSnapshot(),
                users.size(), notified, users.size() - notified, token, snapshot.getContentHash());
    }
    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(
            bizType = CalendarNotifyBatchObjectPermissionProvider.BIZ_TYPE, bizId = "#id", action = "read")
    public CalendarNotifyBatchRespVO getBatch(Long id) {
        return BeanUtils.toBean(requireBatch(id), CalendarNotifyBatchRespVO.class);
    }

    public PageResult<CalendarNotifyBatchRespVO> getBatchPage(CalendarNotifyBatchPageReqVO req) {
        notificationAccess.check(req.getCalendarType(), false);
        requirePage(req);
        var page = batchMapper.selectHistoryPage(req, TenantContextHolder.getRequiredTenantId());
        return new PageResult<>(page.getList().stream().map(row -> BeanUtils.toBean(row, CalendarNotifyBatchRespVO.class)).toList(), page.getTotal());
    }

    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(
            bizType = CalendarNotifyBatchObjectPermissionProvider.BIZ_TYPE, bizId = "#batchId", action = "recipients")
    public PageResult<CalendarNotifyRecipientRespVO> getRecipientPage(Long batchId, PageParam req) {
        requireBatch(batchId);
        requirePage(req);
        var page = recipientMapper.selectBatchPage(batchId, TenantContextHolder.getRequiredTenantId(), req);
        return new PageResult<>(page.getList().stream().map(row -> new CalendarNotifyRecipientRespVO(row.getUserId(),
                row.getUserType(), row.getNicknameSnapshot(), row.getStatus(), row.getSkipReason(), row.getMessageId(),
                row.getCreateTime(), row.getCompletedTime())).toList(), page.getTotal());
    }

    private void requirePage(PageParam req) {
        if (req == null || req.getPageNo() == null || req.getPageNo() < 1 || req.getPageSize() == null
                || req.getPageSize() < 1 || req.getPageSize() > 200) throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
    }

    private CalendarNotifyBatchDO requireBatch(Long id) {
        CalendarNotifyBatchDO batch = batchMapper.selectById(id);
        if (batch == null || !Objects.equals(batch.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CALENDAR_NOTIFY_BATCH_NOT_EXISTS);
        }
        notificationAccess.check(batch.getCalendarType(), false);
        return batch;
    }
    private CalendarNotifySnapshotDO snapshot(CalendarNotifyReqVO req, boolean lock) {
        if ("COURSE".equalsIgnoreCase(req.getCalendarType())) {
            objectAccess.courseNotification(req.getCalendarId());
            CourseCalendarEventDO row = lock ? courseMapper.selectForUpdate(req.getCalendarId()) : courseMapper.selectById(req.getCalendarId());
            if (row == null || Boolean.TRUE.equals(row.getDeleted())) throw exception(CALENDAR_NOTIFY_NOT_EXISTS);
            return lock ? snapshots.captureCourse(row, "MANUAL", "ACTIVE") : CalendarNotificationSnapshotService.projectCourse(row, "MANUAL", "ACTIVE");
        }
        objectAccess.examNotification(req.getCalendarId());
        ExamScheduleDO row = lock ? examMapper.selectForUpdate(req.getCalendarId()) : examMapper.selectById(req.getCalendarId());
        if (row == null || Boolean.TRUE.equals(row.getDeleted())) throw exception(CALENDAR_NOTIFY_NOT_EXISTS);
        if (!"PUBLISHED".equals(row.getRecordStatus())) throw exception(CALENDAR_NOTIFY_STATE_INVALID);
        return lock ? snapshots.captureExam(row, "MANUAL") : CalendarNotificationSnapshotService.projectExam(row, "MANUAL");
    }

    private void validateRequest(CalendarNotifyReqVO req) {
        if (req.getCalendarId() == null || req.getCalendarId() <= 0
                || !("ALL".equalsIgnoreCase(req.getScope()) || "SPECIFIED".equalsIgnoreCase(req.getScope())))
            throw exception(CALENDAR_NOTIFY_REQUEST_INVALID);
        if (req.getUserIds() != null && req.getUserIds().stream().anyMatch(id -> id == null || id <= 0))
            throw exception(CALENDAR_NOTIFY_RECIPIENT_INVALID);
    }

    private List<AdminUserRespDTO> recipients(CalendarNotifyReqVO req) {
        List<AdminUserRespDTO> users;
        if ("ALL".equalsIgnoreCase(req.getScope())) {
            users = adminUserApi.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus());
        } else {
            var requested = req.getUserIds() == null ? Set.<Long>of() : new HashSet<>(req.getUserIds());
            if (requested.isEmpty()) throw exception(CALENDAR_NOTIFY_RECIPIENT_EMPTY);
            users = adminUserApi.getUserList(requested);
            if (!requested.equals(users.stream().map(AdminUserRespDTO::getId).collect(java.util.stream.Collectors.toSet())))
                throw exception(CALENDAR_NOTIFY_RECIPIENT_INVALID);
        }
        if (users.stream().anyMatch(user -> !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())))
            throw exception(CALENDAR_NOTIFY_RECIPIENT_INVALID);
        if (users.isEmpty()) throw exception(CALENDAR_NOTIFY_RECIPIENT_EMPTY);
        return users.stream().sorted(Comparator.comparing(AdminUserRespDTO::getId)).toList();
    }

    private Set<Long> acceptedUsers(CalendarNotifySnapshotDO snapshot) {
        return new HashSet<>(recipientMapper.selectAcceptedUserIds(TenantContextHolder.getRequiredTenantId(),
                snapshot.getCalendarType(), snapshot.getCalendarId(), snapshot.getCalendarVersion()));
    }

    private String requestHash(CalendarNotifyReqVO req, Integer version) {
        var ids = "ALL".equalsIgnoreCase(req.getScope()) || req.getUserIds() == null ? List.<Long>of()
                : req.getUserIds().stream().distinct().sorted().toList();
        return hash(Arrays.asList(getLoginUserId(), req.getCalendarType().toUpperCase(Locale.ROOT), req.getCalendarId(),
                version, req.getScope().toUpperCase(Locale.ROOT), ids, Boolean.TRUE.equals(req.getResend())));
    }

    private String rosterHash(List<AdminUserRespDTO> users) {
        return hash(users.stream().map(AdminUserRespDTO::getId).sorted().toList());
    }

    private String hash(Object value) { return DigestUtil.sha256Hex(JsonUtils.toJsonString(value)); }

    private void verifyPreview(CalendarNotifyReqVO req, CalendarNotifySnapshotDO snapshot, String requestHash, List<AdminUserRespDTO> users) {
        if (req.getPreviewToken() == null || req.getPreviewToken().length() > 128) throw exception(CALENDAR_NOTIFY_PREVIEW_INVALID);
        var preview = previewMapper.selectToken(DigestUtil.sha256Hex(req.getPreviewToken()));
        if (preview == null || !Objects.equals(preview.getOperatorUserId(), getLoginUserId())
                || !Objects.equals(preview.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !preview.getExpiresAt().isAfter(LocalDateTime.now()) || !Objects.equals(preview.getRequestHash(), requestHash))
            throw exception(CALENDAR_NOTIFY_PREVIEW_INVALID);
        if (!Objects.equals(preview.getContentHash(), snapshot.getContentHash())) throw exception(CALENDAR_NOTIFY_VERSION_CONFLICT);
        if (!Objects.equals(preview.getRosterHash(), rosterHash(users))) throw exception(CALENDAR_NOTIFY_ROSTER_CHANGED);
    }

}
