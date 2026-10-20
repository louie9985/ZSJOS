package cn.iocoder.yudao.module.zsjos.service.educationteam;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadTeamTransferPolicy;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamOrganizationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class EducationTeamScope {
 @Resource private EducationTeamOrganizationMapper organizations;
 @Resource private AdminUserApi users;
 @Resource private PermissionApi permissions;
 @Resource private LeadObjectPermissionService managers;
 @Resource private LeadTeamTransferPolicy ownership;
 /** Navigation ownership follows configured business domain, independently of role labels or grants. */
 public boolean deliveryNavigation(Long userId) {
  var user=users.getUser(userId);
  return user!=null && user.getDeptId()!=null && organizations.deliveryDepartments().contains(user.getDeptId());
 }
 public List<AdminUserRespDTO> readable(Long reader) {
  if(!permissions.hasTenantReadAllAccess(reader))return managed(reader);
  var all=new ArrayList<>(users.getUserListByStatus(0));all.addAll(users.getUserListByStatus(1));
  return inDelivery(all);
 }
 public List<AdminUserRespDTO> managed(Long manager) {
  var ids=managers.getManagedUserIds(manager);return ids.isEmpty()?List.of():inDelivery(users.getUserList(ids));
 }
 private List<AdminUserRespDTO> inDelivery(List<AdminUserRespDTO> candidates) {
  var depts=new HashSet<>(organizations.deliveryDepartments());
  return candidates.stream().filter(u->u.getDeptId()!=null&&depts.contains(u.getDeptId())).toList();
 }
 public Set<Long> managedIds(Long manager){return managed(manager).stream().map(AdminUserRespDTO::getId).collect(Collectors.toSet());}
 public AdminUserRespDTO requireManaged(Long id,Long manager){return managed(manager).stream().filter(u->Objects.equals(id,u.getId())).findFirst().orElseThrow(()->exception(EducationTeamErrors.NOT_MANAGED));}
 public void requireEducationLead(LeadDO lead){if(lead==null)throw exception(LEAD_NOT_EXISTS);if(!ownership.isEducation(lead))throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);}
 public void requireReadableLead(LeadDO lead,Long reader){
  requireEducationLead(lead);Long owner=lead.getOwnerUserId()==null?lead.getRecycleSourceOwnerUserId():lead.getOwnerUserId();
  if(readable(reader).stream().noneMatch(u->Objects.equals(owner,u.getId())))throw exception(LEAD_PERMISSION_DENIED);
 }
}
