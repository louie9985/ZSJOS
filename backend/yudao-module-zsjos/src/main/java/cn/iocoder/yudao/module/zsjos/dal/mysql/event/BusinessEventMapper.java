package cn.iocoder.yudao.module.zsjos.dal.mysql.event;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.List;

@Mapper
public interface BusinessEventMapper extends BaseMapperX<BusinessEventDO> {

    default List<BusinessEventDO> selectTodayByUserIds(List<Long> userIds,
            java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (userIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<BusinessEventDO>()
                .select(BusinessEventDO::getOperatorUserId, BusinessEventDO::getAggregateId)
                .in(BusinessEventDO::getOperatorUserId, userIds)
                .ge(BusinessEventDO::getOccurredAt, start).lt(BusinessEventDO::getOccurredAt, end)
                .eq(BusinessEventDO::getAggregateType, "lead")
                .eq(BusinessEventDO::getEventType, cn.iocoder.yudao.module.zsjos.enums.LeadConstants.EVENT_LEAD_QUALIFIED_VALID));
    }

    default BusinessEventDO selectByIdempotencyKeyForUpdate(String key) {
        return selectOne(new LambdaQueryWrapperX<BusinessEventDO>()
                .eq(BusinessEventDO::getIdempotencyKey, key).last("FOR UPDATE"));
    }
    default BusinessEventDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<BusinessEventDO>()
                .eq(BusinessEventDO::getIdempotencyKey, key));
    }

    default List<BusinessEventDO> selectByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<BusinessEventDO>()
                .eq(BusinessEventDO::getAggregateType, "lead")
                .eq(BusinessEventDO::getAggregateId, leadId)
                .orderByDesc(BusinessEventDO::getOccurredAt)
                .orderByDesc(BusinessEventDO::getId));
    }
}
