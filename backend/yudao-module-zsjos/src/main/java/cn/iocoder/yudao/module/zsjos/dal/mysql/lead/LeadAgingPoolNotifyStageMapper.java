package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolNotifyStageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeadAgingPoolNotifyStageMapper extends BaseMapperX<LeadAgingPoolNotifyStageDO> {
    default LeadAgingPoolNotifyStageDO selectByRule(Long leadId, Integer cycleNo, Long ruleId) {
        return selectOne(new LambdaQueryWrapperX<LeadAgingPoolNotifyStageDO>()
                .eq(LeadAgingPoolNotifyStageDO::getLeadId, leadId)
                .eq(LeadAgingPoolNotifyStageDO::getCycleNo, cycleNo)
                .eq(LeadAgingPoolNotifyStageDO::getNotifyRuleId, ruleId));
    }
}
