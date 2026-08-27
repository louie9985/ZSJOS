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
            VALUES (#{date},#{type},LAST_INSERT_ID(1),'',NOW(),'',NOW(),b'0',#{tenantId})
            ON DUPLICATE KEY UPDATE current_value=LAST_INSERT_ID(current_value+1),update_time=NOW()
            """)
    int reserve(@Param("tenantId") Long tenantId, @Param("date") LocalDate date,
                @Param("type") String type);

    @Select("SELECT LAST_INSERT_ID()")
    long selectReservedValue();
}
