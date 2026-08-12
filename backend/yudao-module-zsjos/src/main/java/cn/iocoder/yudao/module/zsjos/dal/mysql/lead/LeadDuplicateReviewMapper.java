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

    default PageResult<LeadDuplicateReviewDO> selectPage(PageParam page, String status) {
        return selectPage(page, new LambdaQueryWrapperX<LeadDuplicateReviewDO>()
                .eqIfPresent(LeadDuplicateReviewDO::getStatus, status)
                .orderByAsc(LeadDuplicateReviewDO::getCreateTime)
                .orderByAsc(LeadDuplicateReviewDO::getId));
    }

    @Select("SELECT * FROM zsjos_lead_duplicate_review WHERE id=#{id} AND tenant_id=#{tenantId} " +
            "AND deleted=b'0' FOR UPDATE")
    LeadDuplicateReviewDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
