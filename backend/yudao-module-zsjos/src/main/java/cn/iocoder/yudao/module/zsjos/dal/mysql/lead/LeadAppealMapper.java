package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LeadAppealMapper extends BaseMapperX<LeadAppealDO> {

    default List<LeadAppealDO> selectListByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<LeadAppealDO>().eq(LeadAppealDO::getLeadId, leadId)
                .orderByAsc(LeadAppealDO::getRoundNo));
    }

    default LeadAppealDO selectLatestByLeadId(Long leadId) {
        return selectOne(new LambdaQueryWrapperX<LeadAppealDO>().eq(LeadAppealDO::getLeadId, leadId)
                .orderByDesc(LeadAppealDO::getRoundNo).last("LIMIT 1"));
    }

    default LeadAppealDO selectBySubmissionIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<LeadAppealDO>()
                .eq(LeadAppealDO::getSubmissionIdempotencyKey, key));
    }

    default LeadAppealDO selectByDecisionIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<LeadAppealDO>()
                .eq(LeadAppealDO::getDecisionIdempotencyKey, key));
    }

    @Select("SELECT * FROM zsjos_lead_appeal WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = b'0' FOR UPDATE")
    LeadAppealDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
