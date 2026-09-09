package cn.iocoder.yudao.module.zsjos.dal.mysql.coursecalendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.coursecalendar.vo.CourseCalendarPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.coursecalendar.CourseCalendarEventDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CourseCalendarEventMapper extends BaseMapperX<CourseCalendarEventDO> {
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
