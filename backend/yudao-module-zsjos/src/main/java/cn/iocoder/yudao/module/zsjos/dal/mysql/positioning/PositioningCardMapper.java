package cn.iocoder.yudao.module.zsjos.dal.mysql.positioning;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface PositioningCardMapper extends BaseMapperX<PositioningCardDO> {
    default List<PositioningCardDO> selectByDirectorAndStudent(Long directorUserId, Long studentPersonId) {
        return selectList(new LambdaQueryWrapperX<PositioningCardDO>()
                .eq(PositioningCardDO::getDirectorUserId, directorUserId)
                .eq(PositioningCardDO::getStudentPersonId, studentPersonId)
                .orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId));
    }
    default List<PositioningCardDO> selectByStudentAndAccountIds(Long studentPersonId, Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<PositioningCardDO>()
                .eq(PositioningCardDO::getStudentPersonId, studentPersonId)
                .in(PositioningCardDO::getAccountId, accountIds)
                .orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId));
    }
    default List<PositioningCardDO> selectRecentByStudentAndAccountIds(Long studentPersonId,
                                                                       Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<PositioningCardDO>()
                .eq(PositioningCardDO::getStudentPersonId, studentPersonId)
                .in(PositioningCardDO::getAccountId, accountIds)
                .orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId)
                .last("LIMIT 100"));
    }
    @Select("SELECT * FROM zsjos_positioning_card WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    PositioningCardDO selectByIdForUpdate(Long id, Long tenantId);
    default PositioningCardDO selectLatestCreatingDraft(Long serviceRelationId, Long accountId, Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<PositioningCardDO>()
                .eq(PositioningCardDO::getServiceRelationId, serviceRelationId)
                .eq(PositioningCardDO::getAccountId, accountId)
                .eq(PositioningCardDO::getTenantId, tenantId)
                .eq(PositioningCardDO::getStatus, "co_creating")
                .orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId)
                .last("LIMIT 1"));
    }
    default PositioningCardDO selectByIpProcessId(String id) { return selectOne(PositioningCardDO::getIpProcessInstanceId, id); }
    default int updateByVersion(PositioningCardDO card, Integer version, String fromStatus) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>()
                .eq(PositioningCardDO::getId, card.getId()).eq(PositioningCardDO::getVersion, version)
                .eq(PositioningCardDO::getStatus, fromStatus)
                .set(PositioningCardDO::getStatus, card.getStatus())
                .set(PositioningCardDO::getIpProcessInstanceId, card.getIpProcessInstanceId())
                .set(PositioningCardDO::getIpReviewerUserId, card.getIpReviewerUserId())
                .set(PositioningCardDO::getOperatorUserId, card.getOperatorUserId())
                .set(PositioningCardDO::getVersion, version + 1));
    }
    default int updateDraftSnapshot(PositioningCardDO card, Integer version, String fromStatus) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>()
                .eq(PositioningCardDO::getId, card.getId()).eq(PositioningCardDO::getVersion, version)
                .eq(PositioningCardDO::getStatus, fromStatus)
                .set(PositioningCardDO::getFieldsSnapshotJson, card.getFieldsSnapshotJson())
                .set(PositioningCardDO::getValuesSnapshotJson, card.getValuesSnapshotJson())
                .set(PositioningCardDO::getDictSnapshotJson, card.getDictSnapshotJson())
                .set(PositioningCardDO::getTrialEndDate, card.getTrialEndDate())
                .set(PositioningCardDO::getLayer1Json, card.getLayer1Json())
                .set(PositioningCardDO::getLayer2Json, card.getLayer2Json())
                .set(PositioningCardDO::getFormulaJson, card.getFormulaJson())
                .set(PositioningCardDO::getFeasibilityJson, card.getFeasibilityJson())
                .set(PositioningCardDO::getContentFormJson, card.getContentFormJson())
                .set(PositioningCardDO::getComplianceJson, card.getComplianceJson())
                .set(PositioningCardDO::getProfessionalRisk, card.getProfessionalRisk())
                .set(PositioningCardDO::getVersion, version + 1));
    }
    default int updateCurrentOperatorByServiceRelations(Collection<Long> serviceRelationIds, Long operatorUserId) {
        if (serviceRelationIds == null || serviceRelationIds.isEmpty()) return 0;
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>()
                .in(PositioningCardDO::getServiceRelationId, serviceRelationIds)
                .ne(PositioningCardDO::getStatus, "archived")
                .set(PositioningCardDO::getOperatorUserId, operatorUserId));
    }
    default PageResult<PositioningCardDO> selectPage(PositioningCardPageReqVO req, Collection<Long> userIds,
                                                     Collection<Long> accountIds, boolean all) {
        LambdaQueryWrapperX<PositioningCardDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(PositioningCardDO::getStatus, req.getStatus());
        if (!all && accountIds.isEmpty()) query.in(PositioningCardDO::getDirectorUserId, userIds);
        if (!all && !accountIds.isEmpty()) query.and(x -> x.in(PositioningCardDO::getDirectorUserId, userIds).or()
                .in(PositioningCardDO::getAccountId, accountIds));
        return selectPage(req, query.orderByDesc(PositioningCardDO::getUpdateTime).orderByDesc(PositioningCardDO::getId));
    }
    default int transition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>().eq(PositioningCardDO::getId, id)
                .eq(PositioningCardDO::getVersion, version).eq(PositioningCardDO::getStatus, from)
                .set(PositioningCardDO::getStatus, to).set(PositioningCardDO::getVersion, version + 1));
    }
    default int transitionWithOperator(Long id, Integer version, String from, String to, Long operatorUserId) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>().eq(PositioningCardDO::getId, id)
                .eq(PositioningCardDO::getVersion, version).eq(PositioningCardDO::getStatus, from)
                .set(PositioningCardDO::getStatus, to)
                .set(PositioningCardDO::getOperatorUserId, operatorUserId)
                .set(PositioningCardDO::getVersion, version + 1));
    }
    default int transitionOperatorReview(Long id, Integer version, String from, String to, Long operatorUserId,
                                         LocalDateTime reviewedAt, String comment) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>().eq(PositioningCardDO::getId, id)
                .eq(PositioningCardDO::getVersion, version).eq(PositioningCardDO::getStatus, from)
                .set(PositioningCardDO::getStatus, to)
                .set(PositioningCardDO::getOperatorReviewedByUserId, operatorUserId)
                .set(PositioningCardDO::getOperatorReviewedAt, reviewedAt)
                .set(PositioningCardDO::getOperatorReviewComment, comment)
                .set(PositioningCardDO::getVersion, version + 1));
    }
    default int advanceVersionNo(Long id, Integer expectedVersion, Integer expectedVersionNo, Integer nextVersionNo) {
        return update(null, new LambdaUpdateWrapper<PositioningCardDO>().eq(PositioningCardDO::getId, id)
                .eq(PositioningCardDO::getVersion, expectedVersion)
                .eq(PositioningCardDO::getVersionNo, expectedVersionNo)
                .set(PositioningCardDO::getVersionNo, nextVersionNo)
                .set(PositioningCardDO::getVersion, expectedVersion + 1));
    }
}
