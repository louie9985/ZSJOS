package cn.iocoder.yudao.module.zsjos.service.homemetric.source;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskService;
import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import java.math.BigDecimal;

@Service
public class BuiltinMetricSources {
 @Resource private PermissionApi permissions;
 @Resource private BusinessTaskService business;
 @Resource private BpmProcessTaskApi bpm;
 @Resource private AdvancedFilterService filters;
 public boolean supports(String key){return Set.of("business_todo","bpm_todo").contains(key);}
 public boolean canRead(String key,Long actor){return permissions.hasAnyPermissions(actor,"bpm_todo".equals(key)?"bpm:task:query":"zsjos:business-task:query");}
 public List<HomeMetricSources.Source> catalog(Long actor){return List.of(
  new HomeMetricSources.Source("business_todo","我的业务待办","",List.of(),List.of(),"/zsjos/tasks/today"),
  new HomeMetricSources.Source("bpm_todo","我的审批待办","",List.of(),List.of(),"/bpm/task/todo")
 ).stream().filter(s->canRead(s.key(),actor)).toList();}
 public MetricUnit validate(HomeMetricSpec.Operand item){
  var op=item.calculation();
  if(op.aggregate().requiresField()||op.period()!=MetricPeriod.Kind.ALL||op.comparison()!=MetricPeriod.Comparison.CURRENT||filters.hasConditions(item.filter()))throw new IllegalArgumentException("内置待办仅支持当前全部待办计数");
  return MetricUnit.of("COUNT");
 }
 public MetricFormula.Result aggregate(HomeMetricSpec.Operand item,MetricPeriod.Range range,Long actor){
  return MetricFormula.Result.ok(BigDecimal.valueOf(details(item,range,actor,1,1).getTotal()));
 }
 public PageResult<Map<String,Object>> details(HomeMetricSpec.Operand item,MetricPeriod.Range range,Long actor,int pageNo,int pageSize){
  validate(item);if(range!=null)throw new IllegalArgumentException("内置待办不支持日期区间");
  if(!canRead(item.calculation().source(),actor))throw new org.springframework.security.access.AccessDeniedException("无权查看待办");
  if(pageNo<1||pageSize<1||pageSize>100)throw new IllegalArgumentException("分页无效");
  if("bpm_todo".equals(item.calculation().source())){
   var page=bpm.getMetricAllTodoTaskPage(actor,pageNo,pageSize);
   return new PageResult<>(page.getList().stream().map(t->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",t.getId());row.put("businessNo",null);row.put("name",t.getName());row.put("status",t.getStatus());return row;}).toList(),page.getTotal());
  }
  var req=new BusinessTaskPageReqVO();req.setStatus("pending");req.setReadScope("SELF");req.setPageNo(pageNo);req.setPageSize(pageSize);
  var page=business.getMyPage(actor,req);
  return new PageResult<>(page.getList().stream().map(t->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",t.getId());row.put("businessNo",null);row.put("name",t.getTitle());row.put("status",t.getStatus());return row;}).toList(),page.getTotal());
 }
}
