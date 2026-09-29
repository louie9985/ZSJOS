package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CALENDAR_NOTIFY_SNAPSHOT_CONFLICT;

/** Caller owns the calendar row lock. Snapshots and current state commit with the business mutation. */
@Service
public class CalendarNotificationSnapshotService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    @Resource private CalendarNotifySnapshotMapper snapshotMapper;
    @Resource private CalendarNotifyStateMapper stateMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public CalendarNotifySnapshotDO captureCourse(CourseCalendarEventDO row, String eventType, String status) {
        return save(projectCourse(row, eventType, status));
    }

    public static CalendarNotifySnapshotDO projectCourse(CourseCalendarEventDO row, String eventType, String status) {
        var details = new TreeMap<String, Object>();
        details.put("courseFormValue", row.getCourseFormValue());
        details.put("courseFormLabelSnapshot", row.getCourseFormLabelSnapshot());
        details.put("startTime", row.getStartTime()); details.put("endTime", row.getEndTime());
        String title = row.getCourseName() + (row.getCourseFormLabelSnapshot() == null ? "" : "（" + row.getCourseFormLabelSnapshot() + "）");
        return fingerprint(new CalendarNotifySnapshotDO().setCalendarType("COURSE").setCalendarId(row.getId())
                .setCalendarVersion(version(row.getCalendarVersion())).setEventType(eventType).setRecordStatus(status)
                .setTitleSnapshot(title).setTimeSnapshot(row.getStartTime().format(TIME) + " - " + row.getEndTime().format(TIME))
                .setRemarkSnapshot(row.getRemark()).setDetailsJson(JsonUtils.toJsonString(details)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CalendarNotifySnapshotDO captureExam(ExamScheduleDO row, String eventType) {
        return save(projectExam(row, eventType));
    }

    public static CalendarNotifySnapshotDO projectExam(ExamScheduleDO row, String eventType) {
        var details = new TreeMap<String, Object>();
        details.put("scheduleType", row.getScheduleType());
        details.put("exactDate", row.getExactDate()); details.put("roughStartDate", row.getRoughStartDate());
        details.put("roughEndDate", row.getRoughEndDate()); details.put("categoryPathSnapshot", row.getCategoryPathSnapshot());
        details.put("selectedSpecsJson", row.getSelectedSpecsJson()); details.put("frozenSkusJson", row.getFrozenSkusJson());
        String title = cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService.displayName(row);
        String time = row.getExactDate() != null ? row.getExactDate().toString() : row.getRoughStartDate() + " - " + row.getRoughEndDate();
        return fingerprint(new CalendarNotifySnapshotDO().setCalendarType("EXAM").setCalendarId(row.getId())
                .setCalendarVersion(version(row.getCalendarVersion())).setEventType(eventType).setRecordStatus(row.getRecordStatus())
                .setTitleSnapshot(title).setTimeSnapshot(time).setRemarkSnapshot(row.getRemark()).setDetailsJson(JsonUtils.toJsonString(details)));
    }

    private CalendarNotifySnapshotDO save(CalendarNotifySnapshotDO snapshot) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        snapshot.setTenantId(tenantId);
        var state = stateMapper.selectForUpdate(snapshot.getCalendarType(), snapshot.getCalendarId());
        if (state != null && state.getCalendarVersion() > snapshot.getCalendarVersion()) throw exception(CALENDAR_NOTIFY_SNAPSHOT_CONFLICT);
        var existing = snapshotMapper.selectVersion(snapshot.getCalendarType(), snapshot.getCalendarId(), snapshot.getCalendarVersion());
        if (existing != null && !Objects.equals(existing.getContentHash(), snapshot.getContentHash())) {
            // Reusing a version for different content must fail rather than silently rewrite historical meaning.
            throw exception(CALENDAR_NOTIFY_SNAPSHOT_CONFLICT);
        }
        if (state == null) {
            state = new CalendarNotifyStateDO().setCalendarType(snapshot.getCalendarType()).setCalendarId(snapshot.getCalendarId())
                    .setCalendarVersion(snapshot.getCalendarVersion()).setRecordStatus(snapshot.getRecordStatus());
            state.setTenantId(tenantId);
            stateMapper.insert(state);
        }
        if (existing == null) snapshotMapper.insert(snapshot);
        else snapshot = existing;
        stateMapper.updateById(state.setCurrentSnapshotId(snapshot.getId()).setCalendarVersion(snapshot.getCalendarVersion())
                .setRecordStatus(snapshot.getRecordStatus()));
        return snapshot;
    }

    private static CalendarNotifySnapshotDO fingerprint(CalendarNotifySnapshotDO snapshot) {
        var input = Arrays.asList(snapshot.getCalendarType(), snapshot.getCalendarId(), snapshot.getCalendarVersion(),
                snapshot.getRecordStatus(), snapshot.getTitleSnapshot(), snapshot.getTimeSnapshot(), snapshot.getRemarkSnapshot(), snapshot.getDetailsJson());
        return snapshot.setContentHash(DigestUtil.sha256Hex(JsonUtils.toJsonString(input)));
    }

    private static int version(Integer version) { return version == null ? 1 : version; }
}
