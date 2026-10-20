package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolEventDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.List;

@Mapper
public interface LeadPublicSeaPoolEventMapper extends BaseMapperX<LeadPublicSeaPoolEventDO> {
    default LeadPublicSeaPoolEventDO selectByIdempotencyKey(String key) {
        return selectOne(LeadPublicSeaPoolEventDO::getIdempotencyKey, key);
    }

    default List<LeadPublicSeaPoolEventDO> selectByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<LeadPublicSeaPoolEventDO>()
                .eq(LeadPublicSeaPoolEventDO::getLeadId, leadId)
                .orderByDesc(LeadPublicSeaPoolEventDO::getOccurredAt)
                .orderByDesc(LeadPublicSeaPoolEventDO::getId));
    }
}
