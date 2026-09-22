package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LeadFollowUpRecordMapper extends BaseMapperX<LeadFollowUpRecordDO> {

    default List<LeadFollowUpRecordDO> selectTodayByUserIds(List<Long> userIds,
            java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (userIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                .select(LeadFollowUpRecordDO::getOperatorUserId)
                .in(LeadFollowUpRecordDO::getOperatorUserId, userIds)
                .ge(LeadFollowUpRecordDO::getOccurredAt, start).lt(LeadFollowUpRecordDO::getOccurredAt, end));
    }

    default LeadFollowUpRecordDO selectFirstByAssignment(Long leadId, Long assignmentId, Long ownerId) {
        return selectOne(new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                .eq(LeadFollowUpRecordDO::getLeadId, leadId)
                .eq(LeadFollowUpRecordDO::getAssignmentHistoryId, assignmentId)
                .eq(LeadFollowUpRecordDO::getOperatorUserId, ownerId)
                .isNotNull(LeadFollowUpRecordDO::getOccurredAt)
                .orderByAsc(LeadFollowUpRecordDO::getOccurredAt)
                .orderByAsc(LeadFollowUpRecordDO::getId).last("LIMIT 1"));
    }

    default LeadFollowUpRecordDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                .eq(LeadFollowUpRecordDO::getIdempotencyKey, key));
    }

    default PageResult<LeadFollowUpRecordDO> selectPageByLeadId(Long leadId, long pageNo, long pageSize) {
        cn.iocoder.yudao.framework.common.pojo.PageParam pageParam =
                new cn.iocoder.yudao.framework.common.pojo.PageParam();
        pageParam.setPageNo(Math.toIntExact(pageNo));
        pageParam.setPageSize(Math.toIntExact(pageSize));
        return selectPage(pageParam,
                new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                        .eq(LeadFollowUpRecordDO::getLeadId, leadId)
                        .orderByDesc(LeadFollowUpRecordDO::getOccurredAt)
                        .orderByDesc(LeadFollowUpRecordDO::getId));
    }

    default List<LeadFollowUpRecordDO> selectListByLeadId(Long leadId) {
        return selectList(new LambdaQueryWrapperX<LeadFollowUpRecordDO>()
                .eq(LeadFollowUpRecordDO::getLeadId, leadId)
                .orderByDesc(LeadFollowUpRecordDO::getOccurredAt)
                .orderByDesc(LeadFollowUpRecordDO::getId));
    }
}
