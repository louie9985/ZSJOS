package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dept.*;
import cn.iocoder.yudao.module.system.api.dept.dto.*;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@Service
public class PerformanceAccess {
 @Resource private PermissionApi permissionApi;
 @Resource private DeptApi deptApi;
 @Resource private PostApi postApi;
 @Resource private AdminUserApi userApi;
 @Resource private PerformanceOrgMapper orgMapper;
 @Resource private PerformanceDetailMapper detailMapper;
 public static ServiceException denied() {return cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.SalesPerformanceConstants.PERMISSION_DENIED);}
 public static ServiceException invalid(String message) {return cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.SalesPerformanceConstants.INVALID_REQUEST,message);}
 public boolean has(String permission) {return permissionApi.hasAnyPermissions(getLoginUserId(),PerformanceDomain.permission(permission));}
 public boolean departmentAllowed(Long deptId) {
  if(deptId==null)return false;
  Long user=getLoginUserId();
  if(permissionApi.hasTenantReadAllAccess(user))return true;
  var scope=permissionApi.getDeptDataPermission(user);
  return scope!=null&&(Boolean.TRUE.equals(scope.getAll())||scope.getDeptIds()!=null&&scope.getDeptIds().contains(deptId));
 }
 public boolean historicalRowAllowed(Query q,Long frozenDept) {
  if("SELF".equals(q.getScopeType()))return true;
  if("USER".equals(q.getScopeType())&&Objects.equals(q.getScopeId(),getLoginUserId())&&has("zsjos:sales-performance:self"))return true;
  // Missing historical organization cannot narrow a tenant-wide reader's already authorized personal query.
  // Scoped readers must still prove frozen-department access; current membership is not historical evidence.
  if("USER".equals(q.getScopeType())&&frozenDept==null){
   Long reader=getLoginUserId();
   if(permissionApi.hasTenantReadAllAccess(reader))return true;
   var scope=permissionApi.getDeptDataPermission(reader);
   return scope!=null&&Boolean.TRUE.equals(scope.getAll());
  }
  return departmentAllowed(frozenDept);
 }
 public boolean commandDepartmentAllowed(Long deptId) {
  var scope=permissionApi.getDeptDataPermission(getLoginUserId());return deptId!=null&&scope!=null&&(Boolean.TRUE.equals(scope.getAll())||scope.getDeptIds()!=null&&scope.getDeptIds().contains(deptId));
 }
 public record HistoricalScope(boolean allDepartments, boolean missingDepartment, Set<Long> departments) {}
 /** Resolve once per request; preserve the personal missing-history exception before SQL pagination. */
 public HistoricalScope historicalScope(Query q) {
  if("SELF".equals(q.getScopeType()) || "USER".equals(q.getScopeType())
    && Objects.equals(q.getScopeId(),getLoginUserId()) && has("zsjos:sales-performance:self"))
   return new HistoricalScope(true,true,Set.of());
  boolean all=permissionApi.hasTenantReadAllAccess(getLoginUserId());
  var scope=all?null:permissionApi.getDeptDataPermission(getLoginUserId());
  all=all || scope!=null && Boolean.TRUE.equals(scope.getAll());
  return new HistoricalScope(all,all && "USER".equals(q.getScopeType()),
    all || scope==null || scope.getDeptIds()==null?Set.of():new HashSet<>(scope.getDeptIds()));
 }
 public void targetWriteObject(String type,Long id){targetObject(type,id);Long deptId=id;if("USER".equals(type)){var u=user(id);if(u==null)throw denied();if(!CommonStatusEnum.ENABLE.getStatus().equals(u.getStatus()))throw invalid("该人员已停用，请刷新后重新设置目标");deptId=u.getDeptId();var scope=permissionApi.getDeptDataPermission(getLoginUserId());if(id.equals(getLoginUserId())&&scope!=null&&Boolean.TRUE.equals(scope.getSelf()))return;}if(!commandDepartmentAllowed(deptId))throw denied();}
 public List<PerformanceOrgDO> orgs() {return orgMapper.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<PerformanceOrgDO>()
   .eq(PerformanceOrgDO::getBusinessDomain, PerformanceDomain.current()));}
 public AdminUserRespDTO user(Long id) {return userApi.getUser(id);}
 public List<AdminUserRespDTO> users(Collection<Long> ids) {return ids.isEmpty()?List.of():userApi.getUserList(ids);}
 /** Historical facts cannot reintroduce disabled users into enabled-only personnel navigation. */
 public List<AdminUserRespDTO> presentationUsers(Collection<Long> ids) {
  if(ids.isEmpty())return List.of();
  Set<Long> departments=domainDepartments();
  return users(new HashSet<>(ids)).stream().filter(u->CommonStatusEnum.ENABLE.getStatus().equals(u.getStatus()))
    .filter(u->ids.contains(u.getId())&&u.getDeptId()!=null&&departments.contains(u.getDeptId())).toList();
 }
 public List<DeptRespDTO> departments(Collection<Long> ids) {return ids.isEmpty()?List.of():deptApi.getDeptList(ids);}
 public java.util.function.BiPredicate<Long,Long> targetVisibility() {
  Long reader=getLoginUserId();boolean self=has("zsjos:sales-performance:self");
  boolean all=permissionApi.hasTenantReadAllAccess(reader);
  var scope=all?null:permissionApi.getDeptDataPermission(reader);
  boolean unrestricted=all||scope!=null&&Boolean.TRUE.equals(scope.getAll());
  Set<Long> departments=scope==null||scope.getDeptIds()==null?Set.of():new HashSet<>(scope.getDeptIds());
  return (user,dept)->self&&Objects.equals(reader,user)||unrestricted||dept!=null&&departments.contains(dept);
 }
 public DeptRespDTO dept(Long id) {return id==null?null:deptApi.getDept(id);}
 public List<DeptRespDTO> departments() {return deptApi.getChildDeptList(0L);}
 public List<AdminUserRespDTO> sales() {
  Set<Long> domain=domainDepartments();
  var post=postApi.getPostByCode(PerformanceDomain.delivery()?"study_planner":"sales_specialist");
  return post==null?List.of():userApi.getUserListByPostIds(List.of(post.getId())).stream()
    .filter(u -> CommonStatusEnum.ENABLE.getStatus().equals(u.getStatus()))
    .filter(u -> u.getDeptId()!=null&&domain.contains(u.getDeptId()))
    .filter(u -> Objects.equals(u.getId(), getLoginUserId()) || departmentAllowed(u.getDeptId())).toList();
 }
 public List<AdminUserRespDTO> visibleEmployees() {
  Set<Long> domain=domainDepartments();
  return userApi.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
    .filter(u -> CommonStatusEnum.ENABLE.getStatus().equals(u.getStatus()))
    .filter(u -> u.getDeptId()!=null && domain.contains(u.getDeptId()) && departmentAllowed(u.getDeptId())).toList();
 }
 public Set<Long> domainDepartments() {return orgs().stream().map(PerformanceOrgDO::getDeptId).collect(java.util.stream.Collectors.toSet());}
 public HistoricalScope reportScope(Query q) {
  return historicalScope(q);
 }
 public Set<Long> enabledUserIds(Collection<Long> ids) {
  if(ids.isEmpty())return Set.of();
  Set<Long> enabled=new HashSet<>();
  userApi.getUserList(ids).stream().filter(u->CommonStatusEnum.ENABLE.getStatus().equals(u.getStatus())).forEach(u->enabled.add(u.getId()));
  return enabled;
 }
 /** Current task responsibility is authorized against current organization, never historical attribution. */
 public Set<Long> currentFollowUpAssignees(Query q) {
  authorize(q,false);
  if("SELF".equals(q.getScopeType()))return Set.of(q.getScopeId());
  var people=PerformanceDomain.delivery()?visibleEmployees():sales();
  if("USER".equals(q.getScopeType()))return people.stream().filter(u->Objects.equals(u.getId(),q.getScopeId()))
    .map(AdminUserRespDTO::getId).collect(java.util.stream.Collectors.toSet());
  Set<Long> departments=new HashSet<>();
  if("DEPT".equals(q.getScopeType()))departments.add(q.getScopeId());
  else orgs().stream().filter(o->Objects.equals(o.getCenterId(),q.getScopeId())).forEach(o->departments.add(o.getDeptId()));
  return people.stream().filter(u->departments.contains(u.getDeptId())).map(AdminUserRespDTO::getId)
    .collect(java.util.stream.Collectors.toSet());
 }
 public PerformanceOrgDO mapping(Long deptId) {
  return orgs().stream().filter(x->Objects.equals(x.getDeptId(),deptId)).findFirst().orElse(null);
 }
 public PerformanceOrgDO owningOrganization(Long deptId) {
  return deptId==null?null:orgMapper.selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<PerformanceOrgDO>().eq(PerformanceOrgDO::getDeptId,deptId));
 }
 public Query authorize(Query q,boolean targets) {
  if(targets && ("SELF".equals(q.getScopeType())||"USER".equals(q.getScopeType()))) {
   var person=user("SELF".equals(q.getScopeType())?getLoginUserId():q.getScopeId());
   if(person==null||mapping(person.getDeptId())==null)throw denied();
  }
  if("SELF".equals(q.getScopeType())){q.setScopeId(getLoginUserId()); if(!targets&&!has("zsjos:sales-performance:self"))throw denied(); return q;}
  if(q.getScopeId()==null)throw invalid("请选择统计对象");
  if(targets&&!has("zsjos:sales-performance-target:query"))throw denied();
  if("USER".equals(q.getScopeType())) {
   var u=user(q.getScopeId()); if(u==null)throw denied();
   if(Objects.equals(u.getId(),getLoginUserId())&&!targets&&has("zsjos:sales-performance:self"))return q;
   if(targets && !departmentAllowed(u.getDeptId()))throw denied();
   if(!targets && !has("zsjos:sales-performance:department") && !has("zsjos:sales-performance:center"))throw denied();
   if(!targets) {var historical=historicalScope(q);if(!historical.allDepartments()&&historical.departments().isEmpty())throw denied();}
  } else {
   var m=mapping(q.getScopeId());
   if(m==null||!q.getScopeType().equals(m.getKind())||!departmentAllowed(m.getDeptId()))throw denied();
   if(!targets&&!has("zsjos:sales-performance:"+("CENTER".equals(q.getScopeType())?"center":"department")))throw denied();
   if("CENTER".equals(q.getScopeType())&&orgs().stream().filter(x->Objects.equals(x.getCenterId(),q.getScopeId())).anyMatch(x->!departmentAllowed(x.getDeptId())))throw denied();
  }
  return q;
 }
 public Long authorizeUnclassified(Long requestedUser) {
  if(!has("zsjos:sales-performance:query") || !has("zsjos:sales-performance:self") || !has("zsjos:sales-performance:detail"))throw denied();
  Long reader=getLoginUserId(), subject=requestedUser==null?reader:requestedUser;
  if(subject==null || !Objects.equals(reader,subject) && !permissionApi.hasTenantReadAllAccess(reader))throw denied();
  return subject;
 }
 public void targetReadObject(PerformanceTargetDO row) {
  if(row==null || !PerformanceDomain.current().equals(row.getBusinessDomain()) || !has("zsjos:sales-performance-target:query"))throw denied();
  if(!targetVisibility().test("USER".equals(row.getScopeType())?row.getScopeId():null,row.getDeptId()))throw denied();
 }
 public void targetObject(String type,Long id) {Query q=new Query(); q.setScopeType(type);q.setScopeId(id);authorize(q,true);}
 public List<Node> tree(boolean targets) {
  List<Node> result=new ArrayList<>(); Set<Long> included=new HashSet<>();
  var organizations=orgs();
  for(var m:organizations) {
   Query q=new Query();q.setScopeType(m.getKind());q.setScopeId(m.getDeptId());
   boolean allowed=true;try{authorize(q,targets);}catch(ServiceException ex){allowed=false;}
   if(!allowed)continue;
   addDept(m.getDeptId(),true,m.getKind(),result,included);
  }
  // Delivery's department-wide personnel scope must not expand current sales target candidates.
  for(var u:PerformanceDomain.delivery()?visibleEmployees():sales()) {
   Query q=new Query();q.setScopeType("USER");q.setScopeId(u.getId());
   try{authorize(q,targets);}catch(ServiceException ex){continue;}
   if(u.getDeptId()!=null)addDept(u.getDeptId(),false,"DEPT",result,included);
   result.add(new Node("USER:"+u.getId(),u.getDeptId()==null?null:"ORG:"+u.getDeptId(),u.getNickname(),"USER",u.getId(),true));
  }
  if(!targets&&(has("zsjos:sales-performance:department")||has("zsjos:sales-performance:center"))) {
   var query=new Query();query.setScopeType("USER");query.setScopeId(-1L);var scope=historicalScope(query);
   var request=new PerformanceDetailQuery().setTenant(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId())
     .setBusinessDomain(PerformanceDomain.current()).setAllDepartments(scope.allDepartments())
     .setMissingDepartment(false).setDepartments(scope.departments());
   Set<Long> present=result.stream().filter(n->"USER".equals(n.scopeType())).map(Node::scopeId).collect(java.util.stream.Collectors.toSet());
   var historical=detailMapper.historicalPeople(request);
   Set<Long> allowed=presentationUsers(historical.stream().map(PerformanceFact::getUserId).filter(Objects::nonNull).toList())
     .stream().map(AdminUserRespDTO::getId).collect(java.util.stream.Collectors.toSet());
   for(var fact:historical)if(allowed.contains(fact.getUserId())&&present.add(fact.getUserId()))
    result.add(new Node("USER:"+fact.getUserId(),null,Objects.toString(fact.getUserName(),"历史人员"),"USER",fact.getUserId(),true));
  }
  if(!targets&&has("zsjos:sales-performance:self"))result.addFirst(new Node("SELF",null,"我的业绩","SELF",getLoginUserId(),true));
  return result;
 }
 private void addDept(Long id,boolean selectable,String kind,List<Node> nodes,Set<Long> seen) {
  if(!seen.add(id)){if(selectable)for(int i=0;i<nodes.size();i++){var n=nodes.get(i);if(n.key().equals("ORG:"+id))nodes.set(i,new Node(n.key(),n.parentKey(),n.title(),kind,id,true));}return;}var d=dept(id);if(d==null)return;
  if(d.getParentId()!=null&&d.getParentId()!=0)addDept(d.getParentId(),false,"DEPT",nodes,seen);
  nodes.add(new Node("ORG:"+id,d.getParentId()==null||d.getParentId()==0?null:"ORG:"+d.getParentId(),d.getName(),kind,id,selectable));
 }
 public void saveOrg(OrgEdit req) {
  var existing=owningOrganization(req.getDeptId());
  if(existing!=null&&!PerformanceDomain.current().equals(existing.getBusinessDomain()))throw denied();
  if(!has("zsjos:sales-performance-target:configure")||!commandDepartmentAllowed(req.getDeptId())||!commandDepartmentAllowed(req.getCenterId()))throw denied();
  var d=dept(req.getDeptId());var c=dept(req.getCenterId());
  if(d==null||c==null||!Objects.equals(d.getStatus(),0)||!Objects.equals(c.getStatus(),0))throw invalid("请选择有效组织");
  if("CENTER".equals(req.getKind())&&!Objects.equals(req.getDeptId(),req.getCenterId()))throw invalid("中心必须关联自身");
  if("DEPT".equals(req.getKind())) {
   var cm=mapping(req.getCenterId());
   if(cm==null||!"CENTER".equals(cm.getKind())||deptApi.getParentDeptList(req.getDeptId()).stream().noneMatch(x->Objects.equals(x.getId(),req.getCenterId())))throw invalid("销售部门必须属于所选中心");
  }
  var row=mapping(req.getDeptId());if(row==null){row=new PerformanceOrgDO();row.setDeptId(req.getDeptId());row.setVersion(0);}
  row.setCenterId(req.getCenterId());row.setKind(req.getKind());
  row.setBusinessDomain(PerformanceDomain.current());
  if(row.getId()==null)orgMapper.insert(row);else orgMapper.updateById(row);
 }
}
