package cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface EducationTeamLeadMapper extends BaseMapperX<LeadDO> {
 default PageResult<LeadDO> page(Long owner,LeadManagementPageReqVO req,List<Long> matched) {
  if(matched!=null&&matched.isEmpty())return PageResult.empty();
  boolean recycled=Boolean.TRUE.equals(req.getRecycledOnly());
  var q=new LambdaQueryWrapperX<LeadDO>().eq(LeadDO::getOwnerIdentity,"education")
   .eqIfPresent(LeadDO::getStatus,req.getStatus())
   .eqIfPresent(LeadDO::getLeadCategory,req.getLeadCategory()).eqIfPresent(LeadDO::getSourceChannelId,req.getSourceChannel());
  if(recycled)q.eq(LeadDO::getRecycleSourceOwnerUserId,owner).eq(LeadDO::getAssignmentStatus,"recycle_pending").isNull(LeadDO::getOwnerUserId);
  else q.eq(LeadDO::getOwnerUserId,owner);
  if(req.getKeyword()!=null&&!req.getKeyword().isBlank())q.and(n->n.like(LeadDO::getLeadNo,req.getKeyword()).or().like(LeadDO::getSubmittedName,req.getKeyword()).or().like(LeadDO::getSubmittedMobile,req.getKeyword()).or().like(LeadDO::getSubmittedWechatId,req.getKeyword()));
  if(matched!=null)q.in(LeadDO::getId,matched);
  q.orderByDesc(LeadDO::getLastActivityAt).orderByDesc(LeadDO::getId);
  return selectPage(req,q);
 }
}
