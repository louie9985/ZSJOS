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
 public static ServiceException denied() {return cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.SalesPerformanceConstants.PERMISSION_DENIED);}
 public static ServiceException invalid(String message) {return cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.SalesPerformanceConstants.INVALID_REQUEST,message);}
 public boolean has(String permission) {return permissionApi.hasAnyPermissions(getLoginUserId(),permission);}
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
 public void targetWriteObject(String type,Long id){targetObject(type,id);Long deptId=id;if("USER".equals(type)){var u=user(id);if(u==null)throw denied();deptId=u.getDeptId();var scope=permissionApi.getDeptDataPermission(getLoginUserId());if(id.equals(getLoginUserId())&&scope!=null&&Boolean.TRUE.equals(scope.getSelf()))return;}if(!commandDepartmentAllowed(deptId))throw denied();}
 public List<PerformanceOrgDO> orgs() {return orgMapper.selectList();}
 public AdminUserRespDTO user(Long id) {return userApi.getUser(id);}
 public DeptRespDTO dept(Long id) {return id==null?null:deptApi.getDept(id);}
 public List<DeptRespDTO> departments() {return deptApi.getChildDeptList(0L);}
 public List<AdminUserRespDTO> sales() {
  var post=postApi.getPostByCode("sales_specialist");
  return post==null?List.of():userApi.getUserListByPostIds(List.of(post.getId()));
 }
 public PerformanceOrgDO mapping(Long deptId) {
  return orgs().stream().filter(x->Objects.equals(x.getDeptId(),deptId)).findFirst().orElse(null);
 }
 public Query authorize(Query q,boolean targets) {
  if("SELF".equals(q.getScopeType())){q.setScopeId(getLoginUserId()); if(!targets&&!has("zsjos:sales-performance:self"))throw denied(); return q;}
  if(q.getScopeId()==null)throw invalid("请选择统计对象");
  if(targets&&!has("zsjos:sales-performance-target:query"))throw denied();
  if("USER".equals(q.getScopeType())) {
   var u=user(q.getScopeId()); if(u==null)throw denied();
   if(Objects.equals(u.getId(),getLoginUserId())&&!targets&&has("zsjos:sales-performance:self"))return q;
   if(!departmentAllowed(u.getDeptId())||(!targets&&!has("zsjos:sales-performance:department")&&!has("zsjos:sales-performance:center")))throw denied();
  } else {
   var m=mapping(q.getScopeId());
   if(m==null||!q.getScopeType().equals(m.getKind())||!departmentAllowed(m.getDeptId()))throw denied();
   if(!targets&&!has("zsjos:sales-performance:"+("CENTER".equals(q.getScopeType())?"center":"department")))throw denied();
   if("CENTER".equals(q.getScopeType())&&orgs().stream().filter(x->Objects.equals(x.getCenterId(),q.getScopeId())).anyMatch(x->!departmentAllowed(x.getDeptId())))throw denied();
  }
  return q;
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
  for(var u:sales()) {
   Query q=new Query();q.setScopeType("USER");q.setScopeId(u.getId());
   try{authorize(q,targets);}catch(ServiceException ex){continue;}
   if(u.getDeptId()!=null)addDept(u.getDeptId(),false,"DEPT",result,included);
   result.add(new Node("USER:"+u.getId(),u.getDeptId()==null?null:"ORG:"+u.getDeptId(),u.getNickname(),"USER",u.getId(),true));
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
  if(row.getId()==null)orgMapper.insert(row);else orgMapper.updateById(row);
 }
}
