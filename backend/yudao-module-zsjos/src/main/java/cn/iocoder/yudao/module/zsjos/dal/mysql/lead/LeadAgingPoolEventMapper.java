package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolEventDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.List;

@Mapper
public interface LeadAgingPoolEventMapper extends BaseMapperX<LeadAgingPoolEventDO> {
    default LeadAgingPoolEventDO selectByIdempotencyKey(String key) {
        return selectOne(LeadAgingPoolEventDO::getIdempotencyKey, key);
    }

    default List<LeadAgingPoolEventDO> selectByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<LeadAgingPoolEventDO>()
                .eq(LeadAgingPoolEventDO::getLeadId, leadId)
                .orderByDesc(LeadAgingPoolEventDO::getOccurredAt)
                .orderByDesc(LeadAgingPoolEventDO::getId));
    }
}
