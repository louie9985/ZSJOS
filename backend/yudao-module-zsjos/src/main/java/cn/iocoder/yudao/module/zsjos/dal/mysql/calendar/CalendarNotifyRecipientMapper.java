package cn.iocoder.yudao.module.zsjos.dal.mysql.calendar;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyRecipientDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

@Mapper
public interface CalendarNotifyRecipientMapper extends BaseMapperX<CalendarNotifyRecipientDO> {
    @Select("""
            SELECT r.* FROM zsjos_calendar_notify_recipient r
            JOIN zsjos_calendar_notify_batch b ON b.id=r.batch_id AND b.tenant_id=r.tenant_id AND b.deleted=0
            WHERE r.tenant_id=#{tenantId} AND r.deleted=0 AND r.user_type=2
              AND b.calendar_type=#{type} AND b.calendar_id=#{id}
            ORDER BY r.id DESC
            """)
    List<CalendarNotifyRecipientDO> selectHistoricalRecipients(@Param("tenantId") Long tenantId,
                                                              @Param("type") String type, @Param("id") Long id);
    default PageResult<CalendarNotifyRecipientDO> selectBatchPage(Long batchId, Long tenantId, PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<CalendarNotifyRecipientDO>()
                .eq(CalendarNotifyRecipientDO::getTenantId, tenantId)
                .eq(CalendarNotifyRecipientDO::getBatchId, batchId)
                .orderByAsc(CalendarNotifyRecipientDO::getId));
    }
    @Select("""
            SELECT DISTINCT r.user_id FROM zsjos_calendar_notify_recipient r
            JOIN zsjos_calendar_notify_batch b ON b.id=r.batch_id AND b.tenant_id=r.tenant_id AND b.deleted=0
            WHERE r.tenant_id=#{tenantId} AND r.deleted=0 AND r.user_type=2
            AND b.calendar_type=#{type} AND b.calendar_id=#{id} AND b.calendar_version=#{version}
            AND (r.accepted=1 OR (r.accepted IS NULL AND r.status IN ('PENDING','SUCCEEDED','FAILED')))
            """)
    List<Long> selectAcceptedUserIds(@Param("tenantId") Long tenantId, @Param("type") String type,
                                   @Param("id") Long id, @Param("version") Integer version);
    default List<CalendarNotifyRecipientDO> selectByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<CalendarNotifyRecipientDO>()
                .eq(CalendarNotifyRecipientDO::getBatchId, batchId)
                .orderByAsc(CalendarNotifyRecipientDO::getId));
    }
}
