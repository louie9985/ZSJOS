package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OpportunityMapper extends BaseMapperX<OpportunityDO> {
    default OpportunityDO selectByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<OpportunityDO>()
                .eq(OpportunityDO::getLeadId, leadId)
                .eq(OpportunityDO::getType, "initial_conversion"));
    }
}
