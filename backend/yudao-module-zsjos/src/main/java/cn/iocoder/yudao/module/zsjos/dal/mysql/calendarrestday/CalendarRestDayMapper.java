package cn.iocoder.yudao.module.zsjos.dal.mysql.calendarrestday;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendarrestday.CalendarRestDayDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;

@Mapper
public interface CalendarRestDayMapper extends BaseMapperX<CalendarRestDayDO> {
    // Ensuring and locking in sorted date order serializes first-write races without a tenant-wide lock.
    @Insert("INSERT INTO zsjos_calendar_rest_day(tenant_id,date,rest,version,creator,updater) VALUES(#{tenantId},#{date},0,0,#{userId},#{userId}) ON DUPLICATE KEY UPDATE tenant_id=VALUES(tenant_id)")
    void ensureRow(@Param("tenantId") Long tenantId, @Param("date") LocalDate date, @Param("userId") Long userId);

    @Select("SELECT * FROM zsjos_calendar_rest_day WHERE tenant_id=#{tenantId} AND date=#{date} AND deleted=0 FOR UPDATE")
    CalendarRestDayDO lock(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);
}
