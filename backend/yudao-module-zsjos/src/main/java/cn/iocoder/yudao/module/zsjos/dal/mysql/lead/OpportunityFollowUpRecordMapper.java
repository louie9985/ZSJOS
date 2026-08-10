package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityFollowUpRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OpportunityFollowUpRecordMapper extends BaseMapperX<OpportunityFollowUpRecordDO> {
    default OpportunityFollowUpRecordDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<OpportunityFollowUpRecordDO>()
                .eq(OpportunityFollowUpRecordDO::getIdempotencyKey, key));
    }
    default List<OpportunityFollowUpRecordDO> selectListByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<OpportunityFollowUpRecordDO>()
                .eq(OpportunityFollowUpRecordDO::getLeadId, leadId)
                .orderByDesc(OpportunityFollowUpRecordDO::getOccurredAt)
                .orderByDesc(OpportunityFollowUpRecordDO::getId));
    }
}
