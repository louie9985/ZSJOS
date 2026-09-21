package cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ContentReviewBatchMapper extends BaseMapperX<ContentReviewBatchDO> {
    @org.apache.ibatis.annotations.Update("UPDATE zsjos_content_review_batch SET account_ids_json=JSON_REMOVE(account_ids_json, JSON_UNQUOTE(JSON_SEARCH(account_ids_json,'one',CAST(#{accountId} AS CHAR)))), version=version+1 WHERE tenant_id=(SELECT tenant_id FROM zsjos_media_account WHERE id=#{accountId}) AND deleted=b'0' AND JSON_CONTAINS(account_ids_json,JSON_ARRAY(#{accountId})) AND status IN ('DRAFT','DIRECTOR_REVIEW','FINAL_REVIEW')")
    int excludeDeletedAccount(@org.apache.ibatis.annotations.Param("accountId") Long accountId);

    default PageResult<ContentReviewBatchDO> selectPage(ContentReviewBatchPageReqVO request,
                                                         Long userId, boolean seeAll,
                                                         Collection<String> currentTaskProcessInstanceIds) {
        LambdaQueryWrapperX<ContentReviewBatchDO> query = new LambdaQueryWrapperX<ContentReviewBatchDO>()
                .eqIfPresent(ContentReviewBatchDO::getStatus, request.getStatus())
                .eqIfPresent(ContentReviewBatchDO::getAccountId, request.getAccountId());
        query.apply("NOT EXISTS (SELECT 1 FROM zsjos_content_review_batch successor "
                        + "WHERE successor.revision_of_batch_id=zsjos_content_review_batch.id "
                        + "AND successor.tenant_id=zsjos_content_review_batch.tenant_id AND successor.deleted=b'0')");
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            query.like(ContentReviewBatchDO::getBatchNo, request.getKeyword().trim());
        }
        if (Boolean.TRUE.equals(request.getMine())) {
            query.eq(ContentReviewBatchDO::getOperatorUserId, userId);
        } else if (!seeAll) {
            query.and(item -> item.eq(ContentReviewBatchDO::getOperatorUserId, userId)
                    .or().eq(ContentReviewBatchDO::getDirectorUserId, userId)
                    .or().apply("EXISTS (SELECT 1 FROM zsjos_content_review_batch_item i "
                            + "WHERE i.batch_id=zsjos_content_review_batch.id "
                            + "AND i.tenant_id=zsjos_content_review_batch.tenant_id AND i.deleted=b'0' "
                            + "AND (i.director_reviewed_by_user_id={0} OR i.final_reviewed_by_user_id={0}))", userId)
                    .or(currentTaskProcessInstanceIds != null && !currentTaskProcessInstanceIds.isEmpty(),
                            task -> task.in(ContentReviewBatchDO::getProcessInstanceId,
                                    currentTaskProcessInstanceIds)));
        }
        return selectPage(request, query.orderByDesc(ContentReviewBatchDO::getId));
    }

    @Select("SELECT * FROM zsjos_content_review_batch WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    ContentReviewBatchDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default List<ContentReviewBatchDO> selectByRevisionOfBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getRevisionOfBatchId, batchId)
                .orderByAsc(ContentReviewBatchDO::getId));
    }

    @Select("SELECT DISTINCT process_definition_key FROM zsjos_content_review_batch "
            + "WHERE tenant_id=#{tenantId} AND deleted=b'0' AND process_definition_key IS NOT NULL "
            + "AND status IN ('DIRECTOR_REVIEW','FINAL_REVIEW')")
    List<String> selectActiveProcessDefinitionKeys(@Param("tenantId") Long tenantId);

    default ContentReviewBatchDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(new LambdaQueryWrapperX<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getProcessInstanceId, processInstanceId));
    }

    default int markSubmitted(ContentReviewBatchDO batch, Integer expectedVersion, String processInstanceId,
                              String definitionId, String definitionKey, Integer definitionVersion,
                              String businessKey, Long directorUserId, String relationSnapshotJson,
                              String contextSnapshotJson, LocalDateTime submittedAt) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getId, batch.getId())
                .eq(ContentReviewBatchDO::getVersion, expectedVersion)
                .eq(ContentReviewBatchDO::getStatus, "DRAFT")
                .set(ContentReviewBatchDO::getStatus, "DIRECTOR_REVIEW")
                .set(ContentReviewBatchDO::getCurrentStage, "DIRECTOR")
                .set(ContentReviewBatchDO::getProcessInstanceId, processInstanceId)
                .set(ContentReviewBatchDO::getProcessDefinitionId, definitionId)
                .set(ContentReviewBatchDO::getProcessDefinitionKey, definitionKey)
                .set(ContentReviewBatchDO::getProcessDefinitionVersion, definitionVersion)
                .set(ContentReviewBatchDO::getBusinessKey, businessKey)
                .set(ContentReviewBatchDO::getDirectorUserId, directorUserId)
                .set(ContentReviewBatchDO::getRelationSnapshotJson, relationSnapshotJson)
                .set(ContentReviewBatchDO::getContextSnapshotJson, contextSnapshotJson)
                .set(ContentReviewBatchDO::getSubmittedAt, submittedAt)
                .set(ContentReviewBatchDO::getVersion, expectedVersion + 1));
    }

    default int markDirectorCompleted(ContentReviewBatchDO batch, LocalDateTime completedAt) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getId, batch.getId())
                .eq(ContentReviewBatchDO::getVersion, batch.getVersion())
                .eq(ContentReviewBatchDO::getStatus, "DIRECTOR_REVIEW")
                .eq(ContentReviewBatchDO::getCurrentStage, "DIRECTOR")
                .set(ContentReviewBatchDO::getStatus, "FINAL_REVIEW")
                .set(ContentReviewBatchDO::getCurrentStage, "FINAL")
                .set(ContentReviewBatchDO::getDirectorCompletedAt, completedAt)
                .set(ContentReviewBatchDO::getVersion, batch.getVersion() + 1));
    }

    default int cancelDraft(ContentReviewBatchDO batch, Integer expectedVersion, LocalDateTime cancelledAt) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getId, batch.getId())
                .eq(ContentReviewBatchDO::getVersion, expectedVersion)
                .eq(ContentReviewBatchDO::getStatus, "DRAFT")
                .eq(ContentReviewBatchDO::getCurrentStage, "DRAFT")
                .set(ContentReviewBatchDO::getStatus, "CANCELLED")
                .set(ContentReviewBatchDO::getCurrentStage, "DONE")
                .set(ContentReviewBatchDO::getFinalizedAt, cancelledAt)
                .set(ContentReviewBatchDO::getVersion, expectedVersion + 1));
    }

    default int finalizeBatch(ContentReviewBatchDO batch, String status, String eventKey,
                              LocalDateTime finalizedAt) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getId, batch.getId())
                .eq(ContentReviewBatchDO::getVersion, batch.getVersion())
                .in(ContentReviewBatchDO::getStatus, "FINAL_REVIEW", "DIRECTOR_REVIEW")
                .set(ContentReviewBatchDO::getStatus, status)
                .set(ContentReviewBatchDO::getCurrentStage, "DONE")
                .set(ContentReviewBatchDO::getLastEventKey, eventKey)
                .set(ContentReviewBatchDO::getFinalCompletedAt, finalizedAt)
                .set(ContentReviewBatchDO::getFinalizedAt, finalizedAt)
                .set(ContentReviewBatchDO::getVersion, batch.getVersion() + 1));
    }

    default int markPublished(ContentReviewBatchDO batch, LocalDateTime completedAt) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getId, batch.getId())
                .eq(ContentReviewBatchDO::getVersion, batch.getVersion())
                .eq(ContentReviewBatchDO::getStatus, "COMPLETED")
                .set(ContentReviewBatchDO::getStatus, "PUBLISHED")
                .set(ContentReviewBatchDO::getCurrentStage, "DONE")
                .set(ContentReviewBatchDO::getFinalizedAt, completedAt)
                .set(ContentReviewBatchDO::getVersion, batch.getVersion() + 1));
    }

    default List<ContentReviewBatchDO> selectByStatusAndStage(Long tenantId, String status, String stage) {
        return selectList(new LambdaQueryWrapperX<ContentReviewBatchDO>()
                .eq(ContentReviewBatchDO::getTenantId, tenantId)
                .eq(ContentReviewBatchDO::getStatus, status)
                .eq(ContentReviewBatchDO::getCurrentStage, stage));
    }
}
