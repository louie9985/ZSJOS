package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.LeadComplaintPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadComplaintDO;
import org.apache.ibatis.annotations.*;
@Mapper public interface LeadComplaintMapper extends BaseMapperX<LeadComplaintDO> {
    default LeadComplaintDO selectByCreateKey(String key) { return selectOne(LeadComplaintDO::getCreateIdempotencyKey, key); }
    default LeadComplaintDO selectByDecisionKey(String key) { return selectOne(LeadComplaintDO::getDecisionIdempotencyKey, key); }
    default PageResult<LeadComplaintDO> selectPage(LeadComplaintPageReqVO req) { return selectPage(req,
            new LambdaQueryWrapperX<LeadComplaintDO>().eqIfPresent(LeadComplaintDO::getStatus, req.getStatus())
                    .orderByDesc(LeadComplaintDO::getUpdateTime).orderByDesc(LeadComplaintDO::getId)); }
    default PageResult<LeadComplaintDO> selectMyPage(LeadComplaintPageReqVO req, Long userId) { return selectPage(req,
            new LambdaQueryWrapperX<LeadComplaintDO>().eq(LeadComplaintDO::getComplainantUserId, userId)
                    .eqIfPresent(LeadComplaintDO::getStatus, req.getStatus())
                    .orderByDesc(LeadComplaintDO::getUpdateTime).orderByDesc(LeadComplaintDO::getId)); }
    default PageResult<LeadComplaintDO> selectPartnerPage(LeadComplaintPageReqVO req, Long partnerId) { return selectPage(req,
            new LambdaQueryWrapperX<LeadComplaintDO>().eq(LeadComplaintDO::getPartnerId, partnerId)
                    .eqIfPresent(LeadComplaintDO::getStatus, req.getStatus())
                    .orderByDesc(LeadComplaintDO::getUpdateTime).orderByDesc(LeadComplaintDO::getId)); }
    default java.util.List<LeadComplaintDO> selectListByLeadId(Long leadId) { return selectList(
            new LambdaQueryWrapperX<LeadComplaintDO>().eq(LeadComplaintDO::getLeadId, leadId)
                    .orderByDesc(LeadComplaintDO::getId)); }
    @Select("SELECT * FROM zsjos_lead_complaint WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    LeadComplaintDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
