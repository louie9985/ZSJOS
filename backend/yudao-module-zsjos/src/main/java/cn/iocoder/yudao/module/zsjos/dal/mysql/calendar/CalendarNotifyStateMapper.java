package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyStateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CalendarNotifyStateMapper extends BaseMapperX<CalendarNotifyStateDO> {
    default CalendarNotifyStateDO selectForUpdate(String type, Long id) {
        return selectOne(new LambdaQueryWrapperX<CalendarNotifyStateDO>()
                .eq(CalendarNotifyStateDO::getCalendarType, type).eq(CalendarNotifyStateDO::getCalendarId, id)
                .last("FOR UPDATE"));
    }
}
