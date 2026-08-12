package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OpportunityMapper extends BaseMapperX<OpportunityDO> {
    default OpportunityDO selectByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<OpportunityDO>()
                .eq(OpportunityDO::getLeadId, leadId)
                .eq(OpportunityDO::getType, "initial_conversion"));
    }

    @Select("SELECT * FROM zsjos_opportunity WHERE lead_id = #{leadId} AND type = 'initial_conversion' " +
            "AND tenant_id = #{tenantId} AND deleted = b'0' LIMIT 1 FOR UPDATE")
    OpportunityDO selectByLeadIdForUpdate(@Param("leadId") Long leadId, @Param("tenantId") Long tenantId);
}
