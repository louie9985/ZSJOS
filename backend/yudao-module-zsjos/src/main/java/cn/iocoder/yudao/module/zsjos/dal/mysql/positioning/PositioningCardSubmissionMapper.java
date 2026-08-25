package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface PositioningCardSubmissionMapper extends BaseMapperX<PositioningCardSubmissionDO> {
    default PositioningCardSubmissionDO selectLatestByCard(Long cardId) {
        return selectOne(new LambdaQueryWrapperX<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getCardId, cardId)
                .orderByDesc(PositioningCardSubmissionDO::getSubmissionNo).last("LIMIT 1"));
    }

    default List<PositioningCardSubmissionDO> selectByStudentAndAccountIds(Long personId,
                                                                            Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getStudentPersonId, personId)
                .in(PositioningCardSubmissionDO::getAccountId, accountIds)
                .orderByDesc(PositioningCardSubmissionDO::getSubmittedAt)
                .orderByDesc(PositioningCardSubmissionDO::getId));
    }

    @Select("SELECT * FROM zsjos_positioning_card_submission WHERE id=#{id} AND tenant_id=#{tenantId} "
            + "AND deleted=b'0' FOR UPDATE")
    PositioningCardSubmissionDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default int markOperatorDecision(Long id, Integer version, String expectedStatus, String status,
                                     Long operatorUserId, LocalDateTime reviewedAt, String comment) {
        return update(null, new LambdaUpdateWrapper<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getId, id).eq(PositioningCardSubmissionDO::getVersion, version)
                .eq(PositioningCardSubmissionDO::getStatus, expectedStatus)
                .set(PositioningCardSubmissionDO::getStatus, status)
                .set(PositioningCardSubmissionDO::getOperatorReviewedByUserId, operatorUserId)
                .set(PositioningCardSubmissionDO::getOperatorReviewedAt, reviewedAt)
                .set(PositioningCardSubmissionDO::getOperatorReviewComment, comment)
                .set(PositioningCardSubmissionDO::getVersion, version + 1));
    }

    default int markStatus(Long id, Integer version, String expectedStatus, String status) {
        return update(null, new LambdaUpdateWrapper<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getId, id).eq(PositioningCardSubmissionDO::getVersion, version)
                .eq(PositioningCardSubmissionDO::getStatus, expectedStatus)
                .set(PositioningCardSubmissionDO::getStatus, status)
                .set(PositioningCardSubmissionDO::getVersion, version + 1));
    }

    default int markStudentDecision(Long id, Integer version, String expectedStatus, String status,
                                    String decision, String comment, LocalDateTime decidedAt) {
        return update(null, new LambdaUpdateWrapper<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getId, id).eq(PositioningCardSubmissionDO::getVersion, version)
                .eq(PositioningCardSubmissionDO::getStatus, expectedStatus)
                .set(PositioningCardSubmissionDO::getStatus, status)
                .set(PositioningCardSubmissionDO::getStudentDecision, decision)
                .set(PositioningCardSubmissionDO::getStudentDecisionComment, comment)
                .set(PositioningCardSubmissionDO::getStudentDecidedAt, decidedAt)
                .set(PositioningCardSubmissionDO::getVersion, version + 1));
    }
}
