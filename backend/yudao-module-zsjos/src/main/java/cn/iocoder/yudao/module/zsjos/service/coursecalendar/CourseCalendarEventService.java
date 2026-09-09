package cn.iocoder.yudao.module.zsjos.service.coursecalendar;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar.CourseCalendarEventMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class CourseCalendarEventService {
    public static final String DICT_TYPE = "zsjos_course_form";
    @Resource private CourseCalendarEventMapper mapper;
    @Resource private DictDataApi dictDataApi;
    @Resource private FileApi fileApi;
    public List<CourseCalendarRespVO> list(CourseCalendarPageReqVO req) {
        if (req.getRangeEnd().isBefore(req.getRangeStart())) throw exception(COURSE_CALENDAR_TIME_INVALID);
        return mapper.selectRange(req).stream().map(this::toResp).toList();
    }
    public CourseCalendarRespVO get(Long id) { CourseCalendarEventDO row=require(id); return toResp(row); }
    @Transactional(rollbackFor=Exception.class) public Long create(CourseCalendarSaveReqVO req) {
        validate(req); CourseCalendarEventDO row=BeanUtils.toBean(req, CourseCalendarEventDO.class)
                .setCourseName(req.getCourseName().trim()).setCourseFormLabelSnapshot(resolveDict(req.getCourseFormValue()))
                .setAttachmentIdsJson(JsonUtils.toJsonString(validateFiles(req.getAttachmentIds()))); mapper.insert(row); return row.getId();
    }
    @Transactional(rollbackFor=Exception.class) public void update(Long id, CourseCalendarSaveReqVO req) {
        validate(req); require(id); CourseCalendarEventDO row=BeanUtils.toBean(req, CourseCalendarEventDO.class).setId(id)
                .setCourseName(req.getCourseName().trim()).setCourseFormLabelSnapshot(resolveDict(req.getCourseFormValue()))
                .setAttachmentIdsJson(JsonUtils.toJsonString(validateFiles(req.getAttachmentIds()))); mapper.updateById(row);
    }
    @Transactional(rollbackFor=Exception.class) public void delete(Long id) { require(id); mapper.deleteById(id); }
    private void validate(CourseCalendarSaveReqVO req) { if (req.getEndTime().isBefore(req.getStartTime())) throw exception(COURSE_CALENDAR_TIME_INVALID); }
    private String resolveDict(String value) { return dictDataApi.getDictDataList(DICT_TYPE).stream().filter(x -> Objects.equals(x.getValue(), value)).findFirst().map(x -> x.getLabel()).orElseThrow(() -> exception(COURSE_CALENDAR_FORM_INVALID)); }
    private List<Long> validateFiles(List<Long> ids) { if(ids==null) return List.of(); for(Long id:ids) if(fileApi.getFileInfo(id)==null) throw exception(COURSE_CALENDAR_ATTACHMENT_INVALID); return ids.stream().distinct().toList(); }
    private CourseCalendarEventDO require(Long id) { CourseCalendarEventDO row=mapper.selectById(id); if(row==null) throw exception(COURSE_CALENDAR_NOT_EXISTS); return row; }
    private CourseCalendarRespVO toResp(CourseCalendarEventDO row) { CourseCalendarRespVO v=BeanUtils.toBean(row, CourseCalendarRespVO.class); v.setAttachmentIds(row.getAttachmentIdsJson()==null?List.of():JsonUtils.parseArray(row.getAttachmentIdsJson(), Long.class)); return v; }
}
