package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeadPublicSeaRecordMapper extends BaseMapperX<LeadPublicSeaRecordDO> {
    default LeadPublicSeaRecordDO selectByLeadId(Long leadId) {
        return selectOne(LeadPublicSeaRecordDO::getLeadId, leadId);
    }
    default void deleteByLeadId(Long leadId) { delete(LeadPublicSeaRecordDO::getLeadId, leadId); }

    @Select("SELECT * FROM zsjos_lead_public_sea_record WHERE lead_id=#{leadId} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    LeadPublicSeaRecordDO selectByLeadIdForUpdate(@Param("leadId") Long leadId,
                                                   @Param("tenantId") Long tenantId);
}
