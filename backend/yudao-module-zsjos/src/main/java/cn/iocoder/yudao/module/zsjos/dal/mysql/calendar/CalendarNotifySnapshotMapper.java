package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifySnapshotDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CalendarNotifySnapshotMapper extends BaseMapperX<CalendarNotifySnapshotDO> {
    default CalendarNotifySnapshotDO selectVersion(String type, Long id, Integer version) {
        return selectOne(new LambdaQueryWrapperX<CalendarNotifySnapshotDO>()
                .eq(CalendarNotifySnapshotDO::getCalendarType, type).eq(CalendarNotifySnapshotDO::getCalendarId, id)
                .eq(CalendarNotifySnapshotDO::getCalendarVersion, version));
    }
}
