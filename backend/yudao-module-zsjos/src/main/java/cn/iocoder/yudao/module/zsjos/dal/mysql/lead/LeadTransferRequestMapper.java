package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeadTransferRequestMapper extends BaseMapperX<LeadTransferRequestDO> {
    default LeadTransferRequestDO selectByIdempotencyKey(String key) {
        return selectOne(LeadTransferRequestDO::getIdempotencyKey, key);
    }
    default LeadTransferRequestDO selectActiveByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<LeadTransferRequestDO>()
                .eq(LeadTransferRequestDO::getLeadId, leadId)
                .eq(LeadTransferRequestDO::getStatus, "pending")
                .orderByDesc(LeadTransferRequestDO::getId).last("LIMIT 1"));
    }
    @Select("SELECT * FROM zsjos_lead_transfer_request WHERE process_instance_id=#{processInstanceId} " +
            "AND tenant_id=#{tenantId} AND deleted=b'0' LIMIT 1 FOR UPDATE")
    LeadTransferRequestDO selectByProcessInstanceIdForUpdate(@Param("processInstanceId") String processInstanceId,
                                                              @Param("tenantId") Long tenantId);
}
