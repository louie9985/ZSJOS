package cn.iocoder.yudao.module.zsjos.service.coursecalendar;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class CourseCalendarEventService {

    public cn.iocoder.yudao.framework.common.pojo.PageResult<CourseCalendarRespVO> search(CourseCalendarSearchReqVO req) {
        var page = mapper.selectSearch(req);
        return new cn.iocoder.yudao.framework.common.pojo.PageResult<>(toResponses(page.getList()), page.getTotal());
    }

    public static final String DICT_TYPE = "zsjos_course_form";
    @Resource private CourseCalendarEventMapper mapper;
    @Resource private DictDataApi dictDataApi;
    @Resource private CourseAttachmentService attachments;
    @Resource private cn.iocoder.yudao.module.zsjos.service.calendar.CalendarCreatorNames creatorNames;
    @Resource private cn.iocoder.yudao.module.zsjos.service.calendar.CalendarNotificationSnapshotService notificationSnapshots;
    public List<CourseCalendarRespVO> list(CourseCalendarPageReqVO req) {
        if (req.getRangeEnd().isBefore(req.getRangeStart())) throw exception(COURSE_CALENDAR_TIME_INVALID);
        return toResponses(mapper.selectRange(req));
    }
    public CourseCalendarRespVO get(Long id) { return toResponses(List.of(require(id))).getFirst(); }

    private List<CourseCalendarRespVO> toResponses(List<CourseCalendarEventDO> rows) {
        if (rows.isEmpty()) return List.of();
        var names = creatorNames.resolve(rows);
        return rows.stream().map(row -> {
            var response = toResp(row);
            response.setCreatorName(names.get(row.getCreator()));
            return response;
        }).toList();
    }
    @Transactional(rollbackFor=Exception.class) public Long create(CourseCalendarSaveReqVO req) {
        CourseCalendarEventDO row = prepareSave(null, req);
        mapper.insert(row);
        notificationSnapshots.captureCourse(row, "CREATED", "ACTIVE");
        return row.getId();
    }
    @Transactional(rollbackFor=Exception.class) public void update(Long id, CourseCalendarSaveReqVO req) {
        CourseCalendarEventDO current = requireLocked(id);
        CourseCalendarEventDO row = prepareSave(current, req);
        notificationSnapshots.captureCourse(current, "MANUAL", "ACTIVE");
        mapper.update(row, new LambdaUpdateWrapper<CourseCalendarEventDO>().eq(CourseCalendarEventDO::getId, id)
                .set(CourseCalendarEventDO::getBackgroundColor, row.getBackgroundColor())
                .set(CourseCalendarEventDO::getRemark, row.getRemark()));
        notificationSnapshots.captureCourse(row, "UPDATED", "ACTIVE");
    }
    @Transactional(rollbackFor=Exception.class) public void delete(Long id) {
        CourseCalendarEventDO current = requireLocked(id);
        notificationSnapshots.captureCourse(current, "MANUAL", "ACTIVE");
        current.setCalendarVersion((current.getCalendarVersion() == null ? 1 : current.getCalendarVersion()) + 1);
        mapper.updateById(new CourseCalendarEventDO().setId(id)
                .setCalendarVersion(current.getCalendarVersion()));
        notificationSnapshots.captureCourse(current, "DELETED", "DELETED");
        mapper.deleteById(id);
    }
    public CourseCalendarEventDO previewSave(Long id, CourseCalendarSaveReqVO req) {
        return prepareSave(id == null ? null : require(id), req);
    }

    private CourseCalendarEventDO prepareSave(CourseCalendarEventDO current, CourseCalendarSaveReqVO req) {
        validate(req);
        // Preview and maintenance share normalization and historical dictionary-snapshot preservation.
        String formLabel = current != null && Objects.equals(current.getCourseFormValue(), req.getCourseFormValue())
                ? current.getCourseFormLabelSnapshot() : resolveDict(req.getCourseFormValue());
        var row = BeanUtils.toBean(req, CourseCalendarEventDO.class).setId(current == null ? null : current.getId())
                .setCourseName(req.getCourseName().trim()).setCourseFormLabelSnapshot(formLabel)
                .setAttachmentIdsJson(attachments.validate(req.getAttachmentIds(), current == null ? null : current.getAttachmentIdsJson(), cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
        row.setBackgroundColor(cn.iocoder.yudao.module.zsjos.service.calendar.CalendarColors.resolve(req.getBackgroundColor(), current == null ? null : current.getBackgroundColor()));
        int version = current == null || current.getCalendarVersion() == null ? 1 : current.getCalendarVersion();
        row.setCalendarVersion(current == null || sameNotificationContent(current, row) ? version : version + 1);
        return row;
    }

    private boolean sameNotificationContent(CourseCalendarEventDO before, CourseCalendarEventDO after) {
        return Objects.equals(before.getCourseName(), after.getCourseName())
                && Objects.equals(before.getCourseFormValue(), after.getCourseFormValue())
                && Objects.equals(before.getCourseFormLabelSnapshot(), after.getCourseFormLabelSnapshot())
                && Objects.equals(before.getStartTime(), after.getStartTime())
                && Objects.equals(before.getEndTime(), after.getEndTime())
                && Objects.equals(before.getRemark(), after.getRemark());
    }
    private CourseCalendarEventDO requireLocked(Long id) {
        CourseCalendarEventDO row = mapper.selectForUpdate(id);
        if (row == null) throw exception(COURSE_CALENDAR_NOT_EXISTS);
        return row;
    }
    private void validate(CourseCalendarSaveReqVO req) { if (req.getEndTime().isBefore(req.getStartTime())) throw exception(COURSE_CALENDAR_TIME_INVALID); }
    private String resolveDict(String value) { return dictDataApi.getDictDataList(DICT_TYPE).stream().filter(x -> Objects.equals(x.getValue(), value)).findFirst().map(x -> x.getLabel()).orElseThrow(() -> exception(COURSE_CALENDAR_FORM_INVALID)); }
    private CourseCalendarEventDO require(Long id) { CourseCalendarEventDO row=mapper.selectById(id); if(row==null) throw exception(COURSE_CALENDAR_NOT_EXISTS); return row; }
    private CourseCalendarRespVO toResp(CourseCalendarEventDO row) { CourseCalendarRespVO v=BeanUtils.toBean(row, CourseCalendarRespVO.class); v.setAttachmentIds(row.getAttachmentIdsJson()==null?List.of():JsonUtils.parseArray(row.getAttachmentIdsJson(), Long.class)); v.setAttachments(attachments.describe(row.getAttachmentIdsJson())); v.setCalendarVersion(row.getCalendarVersion() == null ? 1 : row.getCalendarVersion()); return v; }
}
