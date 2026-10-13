package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarPaletteDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CalendarPaletteMapper extends BaseMapperX<CalendarPaletteDO> {
    // One persistent owner row serializes mutations, including the first concurrent insert.
    @Insert("INSERT INTO zsjos_calendar_palette(tenant_id,user_id,calendar_type,colors_json,creator,updater) VALUES(#{tenant},#{user},#{type},'[]',#{user},#{user}) ON DUPLICATE KEY UPDATE id=id")
    void ensure(@Param("tenant") Long tenant, @Param("user") Long user, @Param("type") String type);
    @Select("SELECT * FROM zsjos_calendar_palette WHERE tenant_id=#{tenant} AND user_id=#{user} AND calendar_type=#{type} AND deleted=0 FOR UPDATE")
    CalendarPaletteDO lock(@Param("tenant") Long tenant, @Param("user") Long user, @Param("type") String type);
}
