package cn.iocoder.yudao.module.zsjos.dal.mysql.content;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Collection;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface ContentVersionMapper extends BaseMapperX<ContentVersionDO> {
    default List<ContentVersionDO> selectByContentId(Long contentId) {
        return selectList(new LambdaQueryWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getContentId, contentId)
                .orderByDesc(ContentVersionDO::getVersionNo));
    }

    default ContentVersionDO selectByContentAndVersionNo(Long contentId, Integer versionNo) {
        return selectOne(new LambdaQueryWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getContentId, contentId)
                .eq(ContentVersionDO::getVersionNo, versionNo));
    }

    default ContentVersionDO selectByContentAndIdempotencyKey(Long contentId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getContentId, contentId)
                .eq(ContentVersionDO::getIdempotencyKey, idempotencyKey));
    }

    @Select("SELECT * FROM zsjos_content_version WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    ContentVersionDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM zsjos_content_version WHERE content_id=#{contentId} AND version_no=#{versionNo} "
            + "AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    ContentVersionDO selectByContentAndVersionNoForUpdate(@Param("contentId") Long contentId,
                                                          @Param("versionNo") Integer versionNo,
                                                          @Param("tenantId") Long tenantId);

    default List<ContentVersionDO> selectListByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ContentVersionDO>()
                .in(ContentVersionDO::getId, ids));
    }

    default List<ContentVersionDO> selectEditableByContentIds(Collection<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ContentVersionDO>()
                .in(ContentVersionDO::getContentId, contentIds)
                .isNull(ContentVersionDO::getFrozenAt)
                .isNull(ContentVersionDO::getReviewDecision));
    }

    default int freeze(Long id, LocalDateTime frozenAt) {
        return update(null, new LambdaUpdateWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getId, id)
                .isNull(ContentVersionDO::getFrozenAt)
                .isNull(ContentVersionDO::getReviewDecision)
                .set(ContentVersionDO::getFrozenAt, frozenAt));
    }

    default int unfreeze(Long id) {
        return update(null, new LambdaUpdateWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getId, id)
                .isNotNull(ContentVersionDO::getFrozenAt)
                .isNull(ContentVersionDO::getReviewDecision)
                .set(ContentVersionDO::getFrozenAt, null));
    }

    default int finishReview(Long id, String decision, String comment, Long reviewerUserId,
                             LocalDateTime reviewedAt) {
        return update(null, new LambdaUpdateWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getId, id)
                .isNotNull(ContentVersionDO::getFrozenAt)
                .isNull(ContentVersionDO::getReviewDecision)
                .set(ContentVersionDO::getReviewDecision, decision)
                .set(ContentVersionDO::getReviewComment, comment)
                .set(ContentVersionDO::getReviewedByUserId, reviewerUserId)
                .set(ContentVersionDO::getReviewedAt, reviewedAt));
    }
}
