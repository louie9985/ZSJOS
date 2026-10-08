package cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamCalendarNoteDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ExamCalendarNoteMapper extends BaseMapperX<ExamCalendarNoteDO> {
    // The unique tenant row serializes first-save races as well as later version checks.
    @Insert("INSERT INTO zsjos_exam_calendar_note(tenant_id,content,version,creator,updater) VALUES (#{tenantId},'',0,#{userId},#{userId}) ON DUPLICATE KEY UPDATE tenant_id=VALUES(tenant_id)")
    void ensureRow(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("SELECT * FROM zsjos_exam_calendar_note WHERE tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    ExamCalendarNoteDO lock(@Param("tenantId") Long tenantId);
}
