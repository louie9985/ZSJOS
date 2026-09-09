package cn.iocoder.yudao.module.zsjos.dal.mysql.content;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCandidatePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewCandidateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceTargetPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceTargetRespVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface ContentMapper extends BaseMapperX<ContentDO> {
    @Select("SELECT * FROM zsjos_content WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    ContentDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
    default List<ContentDO> selectByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ContentDO>()
                .in(ContentDO::getAccountId, accountIds)
                .orderByDesc(ContentDO::getUpdateTime).orderByDesc(ContentDO::getId));
    }
    default List<ContentDO> selectRecentByAccountIds(Collection<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ContentDO>().in(ContentDO::getAccountId, accountIds)
                .orderByDesc(ContentDO::getUpdateTime).orderByDesc(ContentDO::getId).last("LIMIT 100"));
    }
    default int advanceCurrentVersion(Long id, Integer expectedVersion, Integer nextVersion) {
        return update(null, new LambdaUpdateWrapper<ContentDO>().eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, expectedVersion)
                .set(ContentDO::getCurrentVersionNo, nextVersion)
                .set(ContentDO::getVersion, expectedVersion + 1));
    }
    default PageResult<ContentDO> selectPage(ContentPageReqVO req, Collection<Long> userIds, boolean all) {
        LambdaQueryWrapperX<ContentDO> query = new LambdaQueryWrapperX<>();
        query.eqIfPresent(ContentDO::getStatus, req.getStatus());
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(x -> x.like(ContentDO::getContentNo, req.getKeyword())
                    .or().like(ContentDO::getTitle, req.getKeyword())
                    .or().like(ContentDO::getTopic, req.getKeyword()));
        }
        if (!all) query.and(x -> x.in(ContentDO::getOwnerOperatorUserId, userIds).or()
                .in(ContentDO::getFilmingEditorUserId, userIds));
        return selectPage(req, query.orderByDesc(ContentDO::getUpdateTime).orderByDesc(ContentDO::getId));
    }

    default PageResult<ContentReviewCandidateRespVO> selectReviewCandidatePage(
            ContentReviewCandidatePageReqVO req, Long userId, Long tenantId) {
        Page<ContentReviewCandidateRespVO> page = MyBatisUtils.buildPage(req);
        List<ContentReviewCandidateRespVO> rows = selectReviewCandidateRows(page, req, userId, tenantId);
        return new PageResult<>(rows, page.getTotal());
    }

    @SelectProvider(type = CandidateSqlProvider.class, method = "reviewCandidateSql")
    List<ContentReviewCandidateRespVO> selectReviewCandidateRows(
            Page<?> page, @Param("request") ContentReviewCandidatePageReqVO request,
            @Param("userId") Long userId, @Param("tenantId") Long tenantId);

    default PageResult<MaterialReferenceTargetRespVO> selectMaterialReferenceTargetPage(
            MaterialReferenceTargetPageReqVO req, Long userId, Long tenantId, boolean all) {
        Page<MaterialReferenceTargetRespVO> page = MyBatisUtils.buildPage(req);
        List<MaterialReferenceTargetRespVO> rows = selectMaterialReferenceTargetRows(
                page, req, userId, tenantId, all);
        return new PageResult<>(rows, page.getTotal());
    }

    @SelectProvider(type = CandidateSqlProvider.class, method = "referenceTargetSql")
    List<MaterialReferenceTargetRespVO> selectMaterialReferenceTargetRows(
            Page<?> page, @Param("request") MaterialReferenceTargetPageReqVO request,
            @Param("userId") Long userId, @Param("tenantId") Long tenantId, @Param("all") boolean all);

    final class CandidateSqlProvider {
        private CandidateSqlProvider() {
        }

        public static String reviewCandidateSql() {
            return "<script>SELECT c.id, c.content_no AS contentNo, c.account_id AS accountId, "
                    + "c.title, c.topic, c.status, c.current_version_no AS currentVersionNo, "
                    + "v.id AS contentVersionId FROM zsjos_content c "
                    + "JOIN zsjos_content_version v ON v.content_id=c.id AND v.version_no=c.current_version_no "
                    + "AND v.tenant_id=c.tenant_id AND v.deleted=b'0' "
                    + "JOIN zsjos_media_account a ON a.id=c.account_id AND a.tenant_id=c.tenant_id "
                    + "AND a.deleted=b'0' AND a.owner_operator_user_id=#{userId} "
                    + "WHERE c.tenant_id=#{tenantId} AND c.deleted=b'0' AND c.status='acceptance' "
                    + "AND v.frozen_at IS NULL AND v.review_decision IS NULL "
                    + "AND COALESCE(NULLIF(TRIM(v.title_snapshot),''),NULLIF(TRIM(v.topic_snapshot),''),"
                    + "NULLIF(TRIM(c.title),''),NULLIF(TRIM(c.topic),'')) IS NOT NULL "
                    + "AND NULLIF(TRIM(v.script_text),'') IS NOT NULL "
                    + "AND JSON_LENGTH(v.cover_snapshot_json)>0 "
                    + "AND (NULLIF(TRIM(v.deliverable_url),'') IS NOT NULL "
                    + "OR JSON_LENGTH(v.deliverable_snapshot_json)>0) "
                    + "AND v.lead_resource_url LIKE 'https://%' AND v.planned_publish_at IS NOT NULL "
                    + "AND NOT EXISTS (SELECT 1 FROM zsjos_content_review_batch_item i "
                    + "JOIN zsjos_content_review_batch b ON b.id=i.batch_id AND b.tenant_id=i.tenant_id "
                    + "AND b.deleted=b'0' WHERE i.tenant_id=c.tenant_id AND i.deleted=b'0' "
                    + "AND i.content_version_id=v.id AND b.status IN ('DRAFT','DIRECTOR_REVIEW','FINAL_REVIEW')) "
                    + "<if test='request.keyword != null and request.keyword.trim() != \"\"'>"
                    + "AND (c.content_no LIKE CONCAT('%',TRIM(#{request.keyword}),'%') "
                    + "OR c.title LIKE CONCAT('%',TRIM(#{request.keyword}),'%') "
                    + "OR c.topic LIKE CONCAT('%',TRIM(#{request.keyword}),'%')) </if>"
                    + "ORDER BY c.update_time DESC, c.id DESC</script>";
        }

        public static String referenceTargetSql() {
            return "<script>SELECT c.id AS contentId, v.id AS contentVersionId, c.content_no AS contentNo, "
                    + "c.title, v.version_no AS versionNo, v.stage FROM zsjos_content c "
                    + "JOIN zsjos_content_version v ON v.content_id=c.id AND v.version_no=c.current_version_no "
                    + "AND v.tenant_id=c.tenant_id AND v.deleted=b'0' "
                    + "WHERE c.tenant_id=#{tenantId} AND c.deleted=b'0' "
                    + "AND c.status IN ('topic','script','in_production','revising') "
                    + "AND v.frozen_at IS NULL AND v.review_decision IS NULL "
                    + "AND (#{all}=TRUE OR c.owner_operator_user_id=#{userId} "
                    + "OR c.filming_editor_user_id=#{userId} OR EXISTS (SELECT 1 FROM zsjos_media_account a "
                    + "WHERE a.id=c.account_id AND a.tenant_id=c.tenant_id AND a.deleted=b'0' "
                    + "AND (a.director_user_id=#{userId} OR a.owner_operator_user_id=#{userId}))) "
                    + "<if test='request.keyword != null and request.keyword.trim() != \"\"'>"
                    + "AND (c.content_no LIKE CONCAT('%',TRIM(#{request.keyword}),'%') "
                    + "OR c.title LIKE CONCAT('%',TRIM(#{request.keyword}),'%') "
                    + "OR c.topic LIKE CONCAT('%',TRIM(#{request.keyword}),'%')) </if>"
                    + "ORDER BY c.update_time DESC, c.id DESC</script>";
        }
    }
    default int transition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<ContentDO>().eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, version).eq(ContentDO::getStatus, from)
                .set(ContentDO::getStatus, to).set(ContentDO::getVersion, version + 1));
    }
    default int rejectTransition(Long id, Integer version, String from, String to) {
        return update(null, new LambdaUpdateWrapper<ContentDO>().eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, version).eq(ContentDO::getStatus, from)
                .set(ContentDO::getStatus, to).set(ContentDO::getVersion, version + 1)
                .setSql("reject_count = reject_count + 1"));
    }

    default int applyBatchReview(Long id, Integer expectedVersion, boolean approved) {
        LambdaUpdateWrapper<ContentDO> update = new LambdaUpdateWrapper<ContentDO>()
                .eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, expectedVersion)
                .eq(ContentDO::getStatus, "acceptance")
                .set(ContentDO::getStatus, approved ? "ready_to_publish" : "rejected")
                .set(ContentDO::getVersion, expectedVersion + 1);
        if (!approved) update.setSql("reject_count = reject_count + 1");
        return update(null, update);
    }

    default int registerPublished(Long id, Integer expectedVersion, String url, LocalDateTime publishedAt) {
        return update(null, new LambdaUpdateWrapper<ContentDO>()
                .eq(ContentDO::getId, id)
                .eq(ContentDO::getVersion, expectedVersion)
                .eq(ContentDO::getStatus, "ready_to_publish")
                .set(ContentDO::getStatus, "published")
                .set(ContentDO::getPublishedUrl, url)
                .set(ContentDO::getPublishedAt, publishedAt)
                .set(ContentDO::getVersion, expectedVersion + 1));
    }
}
