package cn.iocoder.yudao.module.zsjos.dal.mysql.feedback;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackNoDailyCounterDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;

@Mapper
public interface FeedbackNoDailyCounterMapper extends BaseMapperX<FeedbackNoDailyCounterDO> {

    @Insert("""
            INSERT INTO zsjos_feedback_no_daily_counter
              (sequence_date,feedback_type,current_value,creator,create_time,updater,update_time,deleted,tenant_id)
            VALUES (#{date},#{type},#{minimumValue},'',NOW(),'',NOW(),b'0',#{tenantId})
            ON DUPLICATE KEY UPDATE current_value=GREATEST(current_value+1,#{minimumValue}),
              update_time=NOW(),deleted=b'0',deleted_time=NULL
            """)
    int reserve(@Param("tenantId") Long tenantId, @Param("date") LocalDate date,
                @Param("type") String type, @Param("minimumValue") long minimumValue);

    @Select("""
            SELECT current_value
            FROM zsjos_feedback_no_daily_counter
            WHERE tenant_id=#{tenantId} AND sequence_date=#{date} AND feedback_type=#{type}
            FOR UPDATE
            """)
    long selectReservedValue(@Param("tenantId") Long tenantId, @Param("date") LocalDate date,
                             @Param("type") String type);
}
