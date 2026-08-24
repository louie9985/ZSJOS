package cn.iocoder.yudao.module.zsjos.dal.mysql.event;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.List;

@Mapper
public interface BusinessEventMapper extends BaseMapperX<BusinessEventDO> {
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
