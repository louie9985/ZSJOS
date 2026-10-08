package cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseCalendarPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CourseCalendarEventMapper extends BaseMapperX<CourseCalendarEventDO> {

    default cn.iocoder.yudao.framework.common.pojo.PageResult<CourseCalendarEventDO> selectSearch(
            cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseCalendarSearchReqVO req) {
        var query = new LambdaQueryWrapperX<CourseCalendarEventDO>();
        cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarSearchQuery.keyword(query, req.getKeyword(), "course_name", "remark");
        cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarSearchQuery.dates(query, req, "start_time", "end_time", true);
        query.last(cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.CalendarSearchQuery.order(req, "start_time", "CASE WHEN end_time > start_time THEN DATE_SUB(end_time, INTERVAL 1 MICROSECOND) ELSE end_time END", "id", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))));
        return selectPage(req, query);
    }

    default List<CourseCalendarEventDO> selectRange(CourseCalendarPageReqVO req) {
        return selectList(new LambdaQueryWrapperX<CourseCalendarEventDO>()
                .le(CourseCalendarEventDO::getStartTime, req.getRangeEnd())
                .ge(CourseCalendarEventDO::getEndTime, req.getRangeStart())
                .orderByAsc(CourseCalendarEventDO::getStartTime)
                .orderByAsc(CourseCalendarEventDO::getId));
    }
    default CourseCalendarEventDO selectForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<CourseCalendarEventDO>()
                .eq(CourseCalendarEventDO::getId, id).last("FOR UPDATE"));
    }
}
