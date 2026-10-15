package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
@Service
public class PerformanceTargetService {
 @Resource private PerformanceTargetMapper mapper;
 @Resource private PerformanceRevisionMapper revisions;
 @Resource private PerformanceAccess access;
 public record Period(String type,LocalDate start) {}
 /** One report owns this snapshot; it is never retained across requests or used for writes. */
 public final class Batch {
  private final List<PerformanceTargetDO> rows;
  private final List<PerformanceOrgDO> organizations;
  private final List<cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO> sales;
  private final Map<Long,String> userNames=new HashMap<>(),deptNames=new HashMap<>();
  private final java.util.function.BiPredicate<Long,Long> visible;
  private final Map<String,Target> resolved=new HashMap<>();
  private Batch(Collection<Period> periods,Collection<Long> extraUsers) {
   var dates=periods.stream().map(Period::start).toList();
   rows=dates.isEmpty()?List.of():mapper.selectList(new LambdaQueryWrapperX<PerformanceTargetDO>()
     .between(PerformanceTargetDO::getPeriodStart,Collections.min(dates),Collections.max(dates))
     .in(PerformanceTargetDO::getPeriodType,periods.stream().map(Period::type).distinct().toList()));
   organizations=access.orgs();sales=access.sales();visible=access.targetVisibility();
   Set<Long> users=new HashSet<>(extraUsers),depts=new HashSet<>();
   sales.forEach(x->users.add(x.getId()));
   rows.forEach(x->{if("USER".equals(x.getScopeType()))users.add(x.getScopeId());if(x.getDeptId()!=null)depts.add(x.getDeptId());if(x.getCenterId()!=null)depts.add(x.getCenterId());});
   organizations.forEach(x->{depts.add(x.getDeptId());depts.add(x.getCenterId());});depts.remove(null);users.remove(null);
   access.users(users).forEach(x->userNames.put(x.getId(),x.getNickname()));
   access.departments(depts).forEach(x->deptNames.put(x.getId(),x.getName()));
  }
  private String name(String type,Long id){return "USER".equals(type)?userNames.getOrDefault(id,"历史人员"):deptNames.getOrDefault(id,"历史组织");}
  public Target resolve(String input,Long id,String period,LocalDate start) {
   String type="SELF".equals(input)?"USER":input,key=type+":"+id+":"+period+":"+start;
   var existing=resolved.get(key);if(existing!=null)return existing;
   Target result;
   if("quarter".equals(period)||"year".equals(period)) {
    BigDecimal f=BigDecimal.ZERO,s=BigDecimal.ZERO;int missing=0;
    for(int i=0;i<("year".equals(period)?12:3);i++){var t=resolve(type,id,"month",start.plusMonths(i));f=f.add(z(t.floorAmount()));s=s.add(z(t.sprintAmount()));missing+=t.missing();}
    result=new Target(null,type,id,name(type,id),period,start,f,s,f,s,false,missing==0,missing,null);
   } else result=PerformanceTargetService.this.resolve(type,id,period,start,rows.stream().filter(x->period.equals(x.getPeriodType())&&start.equals(x.getPeriodStart())).toList(),new HashSet<>(),null,this);
   resolved.put(key,result);return result;
  }
 }
 public Batch batch(Collection<Period> requested,Collection<Long> users) {
  Set<Period> periods=new LinkedHashSet<>();
  for(var p:requested)if("quarter".equals(p.type())||"year".equals(p.type()))for(int i=0;i<("year".equals(p.type())?12:3);i++)periods.add(new Period("month",p.start().plusMonths(i)));else periods.add(p);
  return new Batch(periods,users);
 }
 public boolean readableIndividual(String type,Long id,String period,LocalDate start){if(!"USER".equals(type))return true;return rows(period,start).stream().filter(x->type.equals(x.getScopeType())&&id.equals(x.getScopeId())).allMatch(x->access.departmentAllowed(x.getDeptId()));}
 public List<PerformanceTargetDO> rows(String period,LocalDate start) {
  return mapper.selectList(new LambdaQueryWrapperX<PerformanceTargetDO>().eq(PerformanceTargetDO::getPeriodType,period).eq(PerformanceTargetDO::getPeriodStart,start));
 }
 public Target resolve(String type,Long id,String period,LocalDate start) {
  if("SELF".equals(type))type="USER";
  if("quarter".equals(period)||"year".equals(period)) {
   List<Target> months=new ArrayList<>();for(int i=0;i<("year".equals(period)?12:3);i++)months.add(resolve(type,id,"month",start.plusMonths(i)));
   BigDecimal f=BigDecimal.ZERO,s=BigDecimal.ZERO;int missing=0;for(var t:months){f=f.add(z(t.floorAmount()));s=s.add(z(t.sprintAmount()));missing+=t.missing();}
   return new Target(null,type,id,name(type,id),period,start,f,s,f,s,false,missing==0,missing,null);
  }
  return resolve(type,id,period,start,rows(period,start),new HashSet<>(),null);
 }
 private Target resolve(String type,Long id,String period,LocalDate start,List<PerformanceTargetDO> rows,Set<String> path,List<MissingTarget> missingRows) {
  return resolve(type,id,period,start,rows,path,missingRows,null);
 }
 private Target resolve(String type,Long id,String period,LocalDate start,List<PerformanceTargetDO> rows,Set<String> path,List<MissingTarget> missingRows,Batch batch) {
  String key=type+id;if(!path.add(key))throw PerformanceAccess.invalid("目标组织关联存在循环");
  var row=rows.stream().filter(x->type.equals(x.getScopeType())&&id.equals(x.getScopeId())).findFirst().orElse(null);
  if(row!=null&&"USER".equals(type)){var q=new Query();q.setScopeType("USER");q.setScopeId(id);if(!(batch==null?access.historicalRowAllowed(q,row.getDeptId()):batch.visible.test(id,row.getDeptId())))row=null;}
  BigDecimal f=BigDecimal.ZERO,s=BigDecimal.ZERO;int missing=0;
  if("USER".equals(type)) {missing=row==null?1:0;if(row==null&&missingRows!=null)missingRows.add(missingRow(type,id,start,"保底、冲刺目标未设置")); f=row==null?null:row.getFloorAmount();s=row==null?null:row.getSprintAmount();}
  else {
   Set<Long> children=new LinkedHashSet<>();String childType="DEPT";
   if("CENTER".equals(type)) {
    Set<Long> recordedDepartments=new HashSet<>();
    rows.stream().filter(x->!"CENTER".equals(x.getScopeType())&&x.getDeptId()!=null).forEach(x->{recordedDepartments.add(x.getDeptId());if(id.equals(x.getCenterId()))children.add(x.getDeptId());});
    (batch==null?access.orgs():batch.organizations).stream().filter(x->"DEPT".equals(x.getKind())&&id.equals(x.getCenterId())&&!recordedDepartments.contains(x.getDeptId())).forEach(x->children.add(x.getDeptId()));
   } else {
    childType="USER";
    Set<Long> recorded=new HashSet<>();rows.stream().filter(x->"USER".equals(x.getScopeType())).forEach(x->{recorded.add(x.getScopeId());if(id.equals(x.getDeptId()))children.add(x.getScopeId());});
    (batch==null?access.sales():batch.sales).stream().filter(x->id.equals(x.getDeptId())&&!recorded.contains(x.getId())&&Objects.equals(x.getStatus(),0)).forEach(x->children.add(x.getId()));
   }
   if(children.isEmpty()){missing++;if(missingRows!=null)missingRows.add(missingRow(type,id,start,"无可汇总的下级目标"));}
   for(Long child:children){var t=resolve(childType,child,period,start,rows,new HashSet<>(path),missingRows,batch);f=f.add(z(t.floorAmount()));s=s.add(z(t.sprintAmount()));missing+=t.missing();}
  }
  boolean manual=row!=null&&Boolean.TRUE.equals(row.getManual());
  return new Target(row==null?null:row.getId(),type,id,batch==null?name(type,id):batch.name(type,id),period,start,f,s,manual?row.getFloorAmount():f,manual?row.getSprintAmount():s,manual,missing==0,missing,row==null?null:row.getVersion());
 }
 public List<MissingTarget> missing(String type,Long id,LocalDate start){
  List<MissingTarget> result=new ArrayList<>();resolve("SELF".equals(type)?"USER":type,id,"month",start,rows("month",start),new HashSet<>(),result);return result;
 }
 private MissingTarget missingRow(String type,Long id,LocalDate start,String reason){
  var user="USER".equals(type)?access.user(id):null;var dept=access.dept("USER".equals(type)?user==null?null:user.getDeptId():id);
  return new MissingTarget(type,id,name(type,id),dept==null?"组织未记录":dept.getName(),start,reason);
 }
 private static BigDecimal z(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
 private String name(String type,Long id){if("USER".equals(type)){var u=access.user(id);return u==null?"历史人员":u.getNickname();}var d=access.dept(id);return d==null?"历史组织":d.getName();}
 @Transactional(rollbackFor=Exception.class)
 public void save(TargetBatch batch) {
  if(!access.has("zsjos:sales-performance-target:update"))throw PerformanceAccess.denied();
  Set<String> keys=new HashSet<>();
  for(var req:batch.getItems()) {
   access.targetWriteObject(req.getScopeType(),req.getScopeId());
   if(!keys.add(req.getScopeType()+":"+req.getScopeId()+":"+req.getPeriodType()+":"+req.getPeriodStart()))throw PerformanceAccess.invalid("同批目标重复");
   if("week".equals(req.getPeriodType())&&req.getPeriodStart().getDayOfWeek()!=DayOfWeek.MONDAY||"month".equals(req.getPeriodType())&&req.getPeriodStart().getDayOfMonth()!=1)throw PerformanceAccess.invalid("周期起点必须为周一或月初");
   if(req.isRestoreAutomatic()&&"USER".equals(req.getScopeType()))throw PerformanceAccess.invalid("个人目标不能恢复自动汇总");
   if(!req.isRestoreAutomatic()&&(req.getFloorAmount()==null||req.getSprintAmount()==null||req.getSprintAmount().compareTo(req.getFloorAmount())<0))throw PerformanceAccess.invalid("冲刺目标不得低于保底目标");
  }
  for(var req:batch.getItems()) {
   var old=mapper.selectOne(new LambdaQueryWrapperX<PerformanceTargetDO>().eq(PerformanceTargetDO::getScopeType,req.getScopeType()).eq(PerformanceTargetDO::getScopeId,req.getScopeId()).eq(PerformanceTargetDO::getPeriodType,req.getPeriodType()).eq(PerformanceTargetDO::getPeriodStart,req.getPeriodStart()).last("FOR UPDATE"));
   if(old!=null&&!Objects.equals(old.getVersion(),req.getVersion())||old==null&&req.getVersion()!=null)throw PerformanceAccess.invalid("目标已被修改，请刷新后重试");
   if(old!=null&&!access.commandDepartmentAllowed(old.getDeptId()))throw PerformanceAccess.denied();
   String before=old==null?null:JsonUtils.toJsonString(old);
   var row=old==null?new PerformanceTargetDO():old;
   row.setScopeType(req.getScopeType());row.setScopeId(req.getScopeId());row.setPeriodType(req.getPeriodType());row.setPeriodStart(req.getPeriodStart());
   if(old==null){Long dept="USER".equals(req.getScopeType())?access.user(req.getScopeId()).getDeptId():req.getScopeId();row.setDeptId(dept);var mapping=access.mapping(dept);row.setCenterId(mapping==null?null:mapping.getCenterId());}
   row.setManual(!req.isRestoreAutomatic());row.setFloorAmount(req.isRestoreAutomatic()?null:req.getFloorAmount());row.setSprintAmount(req.isRestoreAutomatic()?null:req.getSprintAmount());row.setReason(req.getReason().trim());row.setVersion(old==null?0:old.getVersion()+1);
   if(old==null)mapper.insert(row);else mapper.updateById(row);
   var rev=new PerformanceRevisionDO();rev.setTargetId(row.getId());rev.setBeforeJson(before);rev.setAfterJson(JsonUtils.toJsonString(row));rev.setReason(row.getReason());rev.setOperatorId(getLoginUserId());revisions.insert(rev);
  }
 }
 @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(bizType="sales-performance-target",bizId="#id",action="read")
 public List<PerformanceRevisionDO> history(Long id) {var row=mapper.selectById(id);if(row==null)throw PerformanceAccess.denied();access.targetObject(row.getScopeType(),row.getScopeId());return revisions.selectList(new LambdaQueryWrapperX<PerformanceRevisionDO>().eq(PerformanceRevisionDO::getTargetId,id).orderByDesc(PerformanceRevisionDO::getId));}
}
