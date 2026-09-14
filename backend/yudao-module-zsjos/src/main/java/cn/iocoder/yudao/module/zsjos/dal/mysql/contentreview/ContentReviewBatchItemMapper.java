package cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchItemDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ContentReviewBatchItemMapper extends BaseMapperX<ContentReviewBatchItemDO> {

    default List<ContentReviewBatchItemDO> selectByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<ContentReviewBatchItemDO>()
                .eq(ContentReviewBatchItemDO::getBatchId, batchId)
                .orderByAsc(ContentReviewBatchItemDO::getSortNo)
                .orderByAsc(ContentReviewBatchItemDO::getId));
    }

    default ContentReviewBatchItemDO selectByContentVersionId(Long versionId) {
        return selectOne(new LambdaQueryWrapperX<ContentReviewBatchItemDO>()
                .eq(ContentReviewBatchItemDO::getContentVersionId, versionId)
                .orderByDesc(ContentReviewBatchItemDO::getId).last("LIMIT 1"));
    }

    default List<ContentReviewBatchItemDO> selectByBatchIds(Collection<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ContentReviewBatchItemDO>()
                .in(ContentReviewBatchItemDO::getBatchId, batchIds)
                .orderByAsc(ContentReviewBatchItemDO::getBatchId)
                .orderByAsc(ContentReviewBatchItemDO::getSortNo));
    }

    @Select("SELECT * FROM zsjos_content_review_batch_item WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    ContentReviewBatchItemDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default long countActiveByContentVersions(Collection<Long> contentVersionIds) {
        if (contentVersionIds == null || contentVersionIds.isEmpty()) return 0;
        return selectCount(new LambdaQueryWrapperX<ContentReviewBatchItemDO>()
                .in(ContentReviewBatchItemDO::getContentVersionId, contentVersionIds)
                .apply("EXISTS (SELECT 1 FROM zsjos_content_review_batch b WHERE b.id=zsjos_content_review_batch_item.batch_id "
                        + "AND b.tenant_id=zsjos_content_review_batch_item.tenant_id AND b.deleted=b'0' "
                        + "AND b.status IN ('DRAFT','DIRECTOR_REVIEW','FINAL_REVIEW'))"));
    }

    default long countActiveByContentVersionsExcludingBatch(Collection<Long> contentVersionIds, Long batchId) {
        if (contentVersionIds == null || contentVersionIds.isEmpty()) return 0;
        return selectCount(new LambdaQueryWrapperX<ContentReviewBatchItemDO>()
                .in(ContentReviewBatchItemDO::getContentVersionId, contentVersionIds)
                .ne(ContentReviewBatchItemDO::getBatchId, batchId)
                .apply("EXISTS (SELECT 1 FROM zsjos_content_review_batch b WHERE b.id=zsjos_content_review_batch_item.batch_id "
                        + "AND b.tenant_id=zsjos_content_review_batch_item.tenant_id AND b.deleted=b'0' "
                        + "AND b.status IN ('DRAFT','DIRECTOR_REVIEW','FINAL_REVIEW'))"));
    }

    default int updateDirectorDecision(Long id, Long batchId, Integer expectedVersion, String decision,
                                       String comment, Long userId, LocalDateTime reviewedAt) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchItemDO>()
                .eq(ContentReviewBatchItemDO::getId, id)
                .eq(ContentReviewBatchItemDO::getBatchId, batchId)
                .eq(ContentReviewBatchItemDO::getVersion, expectedVersion)
                .isNull(ContentReviewBatchItemDO::getResultStatus)
                .set(ContentReviewBatchItemDO::getDirectorDecision, decision)
                .set(ContentReviewBatchItemDO::getDirectorComment, comment)
                .set(ContentReviewBatchItemDO::getDirectorReviewedByUserId, userId)
                .set(ContentReviewBatchItemDO::getDirectorReviewedAt, reviewedAt)
                .set(ContentReviewBatchItemDO::getVersion, expectedVersion + 1));
    }

    default int updateFinalDecision(Long id, Long batchId, Integer expectedVersion, String decision,
                                    String comment, boolean collectMaterial, String collectionSnapshotJson, Long userId,
                                    LocalDateTime reviewedAt) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchItemDO>()
                .eq(ContentReviewBatchItemDO::getId, id)
                .eq(ContentReviewBatchItemDO::getBatchId, batchId)
                .eq(ContentReviewBatchItemDO::getVersion, expectedVersion)
                .eq(ContentReviewBatchItemDO::getDirectorDecision, "APPROVED")
                .isNull(ContentReviewBatchItemDO::getResultStatus)
                .set(ContentReviewBatchItemDO::getFinalDecision, decision)
                .set(ContentReviewBatchItemDO::getFinalComment, comment)
                .set(ContentReviewBatchItemDO::getCollectMaterial, collectMaterial)
                .set(ContentReviewBatchItemDO::getCollectionSnapshotJson, collectionSnapshotJson)
                .set(ContentReviewBatchItemDO::getFinalReviewedByUserId, userId)
                .set(ContentReviewBatchItemDO::getFinalReviewedAt, reviewedAt)
                .set(ContentReviewBatchItemDO::getVersion, expectedVersion + 1));
    }

    default int finalizeItem(ContentReviewBatchItemDO item, String resultStatus,
                             Long materialId, Long materialVersionId) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchItemDO>()
                .eq(ContentReviewBatchItemDO::getId, item.getId())
                .eq(ContentReviewBatchItemDO::getVersion, item.getVersion())
                .isNull(ContentReviewBatchItemDO::getResultStatus)
                .set(ContentReviewBatchItemDO::getResultStatus, resultStatus)
                .set(ContentReviewBatchItemDO::getCollectedMaterialId, materialId)
                .set(ContentReviewBatchItemDO::getCollectedMaterialVersionId, materialVersionId)
                .set(ContentReviewBatchItemDO::getVersion, item.getVersion() + 1));
    }

    default boolean existsReviewedBy(Long batchId, Long userId) {
        return selectCount(new LambdaQueryWrapperX<ContentReviewBatchItemDO>()
                .eq(ContentReviewBatchItemDO::getBatchId, batchId)
                .and(item -> item.eq(ContentReviewBatchItemDO::getDirectorReviewedByUserId, userId)
                        .or().eq(ContentReviewBatchItemDO::getFinalReviewedByUserId, userId))) > 0;
    }

    default int markPublished(ContentReviewBatchItemDO item, String platformUrl,
                              LocalDateTime publishedAt, Long userId) {
        return update(null, new LambdaUpdateWrapper<ContentReviewBatchItemDO>()
                .eq(ContentReviewBatchItemDO::getId, item.getId())
                .eq(ContentReviewBatchItemDO::getVersion, item.getVersion())
                .eq(ContentReviewBatchItemDO::getResultStatus, "READY_TO_PUBLISH")
                .set(ContentReviewBatchItemDO::getResultStatus, "PUBLISHED")
                .set(ContentReviewBatchItemDO::getPublishedPlatformUrl, platformUrl)
                .set(ContentReviewBatchItemDO::getPublishedAt, publishedAt)
                .set(ContentReviewBatchItemDO::getPublishedByUserId, userId)
                .set(ContentReviewBatchItemDO::getVersion, item.getVersion() + 1));
    }
}
