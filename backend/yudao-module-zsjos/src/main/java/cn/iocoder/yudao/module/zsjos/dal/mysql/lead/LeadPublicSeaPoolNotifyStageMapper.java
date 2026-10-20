package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolNotifyStageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadPublicSeaPoolNotifyStageMapper extends BaseMapperX<LeadPublicSeaPoolNotifyStageDO> {
    default LeadPublicSeaPoolNotifyStageDO selectByRule(Long leadId, Integer cycleNo, Long ruleId) {
        return selectOne(new LambdaQueryWrapperX<LeadPublicSeaPoolNotifyStageDO>()
                .eq(LeadPublicSeaPoolNotifyStageDO::getLeadId, leadId)
                .eq(LeadPublicSeaPoolNotifyStageDO::getCycleNo, cycleNo)
                .eq(LeadPublicSeaPoolNotifyStageDO::getNotifyRuleId, ruleId));
    }
}
