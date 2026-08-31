package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDuplicateReviewDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeadDuplicateReviewMapper extends BaseMapperX<LeadDuplicateReviewDO> {
    default LeadDuplicateReviewDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<LeadDuplicateReviewDO>()
                .eq(LeadDuplicateReviewDO::getSubmissionIdempotencyKey, key));
    }

    default LeadDuplicateReviewDO selectPendingByFingerprint(String fingerprint) {
        return selectOne(new LambdaQueryWrapperX<LeadDuplicateReviewDO>()
                .eq(LeadDuplicateReviewDO::getStatus, "pending")
                .eq(LeadDuplicateReviewDO::getReviewFingerprint, fingerprint)
                .orderByAsc(LeadDuplicateReviewDO::getId)
                .last("LIMIT 1"));
    }

    default PageResult<LeadDuplicateReviewDO> selectPage(PageParam page, String status, java.util.List<Long> matchedIds) {
        LambdaQueryWrapperX<LeadDuplicateReviewDO> query = new LambdaQueryWrapperX<LeadDuplicateReviewDO>()
                .eqIfPresent(LeadDuplicateReviewDO::getStatus, status);
        if (matchedIds != null) {
            if (matchedIds.isEmpty()) query.eq(LeadDuplicateReviewDO::getId, -1L);
            else query.in(LeadDuplicateReviewDO::getId, matchedIds);
        }
        return selectPage(page, query
                .orderByDesc(LeadDuplicateReviewDO::getUpdateTime)
                .orderByDesc(LeadDuplicateReviewDO::getId));
    }

    @Select("SELECT * FROM zsjos_lead_duplicate_review WHERE id=#{id} AND tenant_id=#{tenantId} " +
            "AND deleted=b'0' FOR UPDATE")
    LeadDuplicateReviewDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
