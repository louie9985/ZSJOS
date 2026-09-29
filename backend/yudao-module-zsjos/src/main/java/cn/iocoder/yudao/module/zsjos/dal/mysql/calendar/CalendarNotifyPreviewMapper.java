package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyPreviewDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CalendarNotifyPreviewMapper extends BaseMapperX<CalendarNotifyPreviewDO> {
    default CalendarNotifyPreviewDO selectToken(String hash) {
        return selectOne(CalendarNotifyPreviewDO::getTokenHash, hash);
    }
}
