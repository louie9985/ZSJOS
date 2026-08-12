package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Mapper
public interface LeadAssignmentHistoryMapper extends BaseMapperX<LeadAssignmentHistoryDO> {
    default List<Long> selectTriedSalesUserIds(Long leadId) {
        return selectList(new LambdaQueryWrapperX<LeadAssignmentHistoryDO>()
                .eq(LeadAssignmentHistoryDO::getLeadId, leadId)
                .eq(LeadAssignmentHistoryDO::getActionType, "dispatch")
                .isNotNull(LeadAssignmentHistoryDO::getCandidateUserId)).stream()
                .map(LeadAssignmentHistoryDO::getCandidateUserId).distinct().toList();
    }

    default Map<Long, LeadAssignmentHistoryDO> selectLatestDispatchByLeadIds(List<Long> leadIds) {
        Map<Long, LeadAssignmentHistoryDO> result = new LinkedHashMap<>();
        if (leadIds.isEmpty()) return result;
        selectList(new LambdaQueryWrapperX<LeadAssignmentHistoryDO>()
                .in(LeadAssignmentHistoryDO::getLeadId, leadIds)
                .eq(LeadAssignmentHistoryDO::getActionType, "dispatch")
                .orderByDesc(LeadAssignmentHistoryDO::getOccurredAt)
                .orderByDesc(LeadAssignmentHistoryDO::getId))
                .forEach(item -> result.putIfAbsent(item.getLeadId(), item));
        return result;
    }

}
