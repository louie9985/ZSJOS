package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCompatibilityRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeadPublicSeaPoolCompatibilityRecordMapper extends BaseMapperX<LeadPublicSeaPoolCompatibilityRecordDO> {
    default LeadPublicSeaPoolCompatibilityRecordDO selectByLeadId(Long leadId) {
        return selectOne(LeadPublicSeaPoolCompatibilityRecordDO::getLeadId, leadId);
    }
    default void deleteByLeadId(Long leadId) { delete(LeadPublicSeaPoolCompatibilityRecordDO::getLeadId, leadId); }

    @Select("SELECT * FROM zsjos_lead_public_sea_pool_compatibility_record WHERE lead_id=#{leadId} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    LeadPublicSeaPoolCompatibilityRecordDO selectByLeadIdForUpdate(@Param("leadId") Long leadId,
                                                   @Param("tenantId") Long tenantId);
}
