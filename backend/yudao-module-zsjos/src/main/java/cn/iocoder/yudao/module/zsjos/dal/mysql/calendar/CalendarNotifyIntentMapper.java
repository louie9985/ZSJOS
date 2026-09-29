package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyIntentDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CalendarNotifyIntentMapper extends BaseMapperX<CalendarNotifyIntentDO> {
    default CalendarNotifyIntentDO selectOperation(String key) {
        return selectOne(CalendarNotifyIntentDO::getOperationKey, key);
    }
    default CalendarNotifyIntentDO selectForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<CalendarNotifyIntentDO>()
                .eq(CalendarNotifyIntentDO::getId, id).last("FOR UPDATE"));
    }
    @Select("""
            SELECT id FROM zsjos_calendar_notify_intent
            WHERE tenant_id=#{tenantId} AND deleted=0 AND status='PENDING' AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at,id LIMIT 50
            """)
    List<Long> selectDue(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE zsjos_calendar_notify_intent SET status=#{status},batch_id=#{batchId},attempt_count=#{attempts},
              next_attempt_at=#{next},last_error_code=#{code},completed_time=#{completed},update_time=CURRENT_TIMESTAMP
            WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=0 AND status='PENDING'
            """)
    int updatePending(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("status") String status,
                      @Param("batchId") Long batchId, @Param("attempts") int attempts,
                      @Param("next") LocalDateTime next, @Param("code") String code,
                      @Param("completed") LocalDateTime completed);
}
