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

    default List<LeadAssignmentHistoryDO> selectTodayByUserIds(List<Long> userIds,
            java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (userIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<LeadAssignmentHistoryDO>()
                .select(LeadAssignmentHistoryDO::getLeadId, LeadAssignmentHistoryDO::getCandidateUserId, LeadAssignmentHistoryDO::getActionType)
                .in(LeadAssignmentHistoryDO::getCandidateUserId, userIds)
                .ge(LeadAssignmentHistoryDO::getOccurredAt, start).lt(LeadAssignmentHistoryDO::getOccurredAt, end));
    }

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

    default LeadAssignmentHistoryDO selectLatestDispatch(Long leadId, boolean forUpdate) {
        return selectOne(new LambdaQueryWrapperX<LeadAssignmentHistoryDO>()
                .eq(LeadAssignmentHistoryDO::getLeadId, leadId)
                .eq(LeadAssignmentHistoryDO::getActionType, "dispatch")
                .orderByDesc(LeadAssignmentHistoryDO::getOccurredAt)
                .orderByDesc(LeadAssignmentHistoryDO::getId)
                .last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1"));
    }

    default List<LeadAssignmentHistoryDO> selectByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<LeadAssignmentHistoryDO>()
                .eq(LeadAssignmentHistoryDO::getLeadId, leadId)
                .orderByDesc(LeadAssignmentHistoryDO::getOccurredAt)
                .orderByDesc(LeadAssignmentHistoryDO::getId));
    }

}
