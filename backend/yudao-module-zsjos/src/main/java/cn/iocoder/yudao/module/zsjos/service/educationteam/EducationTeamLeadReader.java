package cn.iocoder.yudao.module.zsjos.service.educationteam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamLeadMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
@Service
public class EducationTeamLeadReader {
 @Resource private EducationTeamScope scope;
 @Resource private EducationTeamLeadMapper leads;
 @Resource private LeadManagementService details;
 @Resource private AdvancedFilterService filters;
 @Resource private PermissionApi permissions;
 @Resource private EducationTeamLeadOverturnService overturn;
 public PageResult<LeadManagementRespVO> page(Long owner,LeadManagementPageReqVO req,Long reader){
  if(scope.readable(reader).stream().noneMatch(u->Objects.equals(u.getId(),owner)))throw exception(LEAD_PERMISSION_DENIED);
  var page=leads.page(owner,req,filters.matchLeadIds(req.getAdvancedFilter()));
  return new PageResult<>(page.getList().stream().map(row->get(row.getId(),reader)).toList(),page.getTotal());
 }
 public LeadManagementRespVO get(Long id,Long reader){
  var lead=leads.selectById(id);scope.requireReadableLead(lead,reader);
  var result=details.getLead(id,reader);var actions=new ArrayList<LeadManagementRespVO.ActionVO>();
  if(scope.managedIds(reader).contains(lead.getOwnerUserId()==null?lead.getRecycleSourceOwnerUserId():lead.getOwnerUserId())){
   for(var action:List.of(EducationTeamLeadActionPolicy.Action.TRANSFER,EducationTeamLeadActionPolicy.Action.RESTORE,EducationTeamLeadActionPolicy.Action.RECYCLE))
    if(permissions.hasAnyPermissions(reader,"zsjos:education-team:lead-"+action.name().toLowerCase(Locale.ROOT))&&EducationTeamLeadActionPolicy.isAllowed(action,lead))actions.add(new LeadManagementRespVO.ActionVO("EDUCATION_TEAM_"+action.name(),true));
   var valid=overturn.action(lead,reader);if(valid!=null){valid.setCode("EDUCATION_TEAM_OVERTURN_VALID");actions.add(valid);}
  }
  result.setAvailableActions(actions);return result;
 }
}
