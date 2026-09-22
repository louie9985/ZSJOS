package cn.iocoder.yudao.module.zsjos.controller.admin.performance;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.service.performance.*;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @Validated @RequestMapping("/zsjos/sales-performance-target")
@PreAuthorize("@ss.hasPermission('zsjos:sales-performance-target:query')")
public class SalesPerformanceTargetController {
 @Resource private PerformanceAccess access;
 @Resource private PerformanceTargetService service;
 @GetMapping("/tree") public CommonResult<List<Node>> tree(){return success(access.tree(true));}
 @GetMapping("/list") public CommonResult<List<Target>> list(@RequestParam String periodType,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate periodStart){
  if("week".equals(periodType)&&periodStart.getDayOfWeek()!=DayOfWeek.MONDAY||"month".equals(periodType)&&periodStart.getDayOfMonth()!=1)throw PerformanceAccess.invalid("周期起点必须为周一或月初");
  if(!Set.of("week","month").contains(periodType))throw PerformanceAccess.invalid("目标周期无效");
  return success(access.tree(true).stream().filter(Node::selectable).filter(n->service.readableIndividual(n.scopeType(),n.scopeId(),periodType,periodStart)).map(n->service.resolve(n.scopeType(),n.scopeId(),periodType,periodStart)).toList());
 }
 @PutMapping("/batch") @PreAuthorize("@ss.hasPermission('zsjos:sales-performance-target:update')") public CommonResult<Boolean> save(@Valid @RequestBody TargetBatch batch){service.save(batch);return success(true);}
 @PutMapping("/organization") @PreAuthorize("@ss.hasPermission('zsjos:sales-performance-target:configure')") public CommonResult<Boolean> org(@Valid @RequestBody OrgEdit req){access.saveOrg(req);return success(true);}
 @GetMapping("/organizations") public CommonResult<List<Organization>> organizations(){return success(access.orgs().stream().filter(x->access.departmentAllowed(x.getDeptId())).map(x->new Organization(x.getDeptId(),x.getCenterId(),x.getKind())).toList());}
 @GetMapping("/organization-candidates") @PreAuthorize("@ss.hasPermission('zsjos:sales-performance-target:configure')") public CommonResult<List<Node>> candidates(){return success(access.departments().stream().filter(x->access.departmentAllowed(x.getId())).map(x->new Node("ORG:"+x.getId(),"ORG:"+x.getParentId(),x.getName(),"DEPT",x.getId(),true)).toList());}
 @GetMapping("/history") public CommonResult<List<Revision>> history(@RequestParam Long id){return success(service.history(id).stream().map(x->new Revision(x.getId(),x.getReason(),Objects.toString(x.getBeforeJson(),""),x.getAfterJson(),access.user(x.getOperatorId())==null?"历史人员":access.user(x.getOperatorId()).getNickname(),x.getCreateTime())).toList());}
}
