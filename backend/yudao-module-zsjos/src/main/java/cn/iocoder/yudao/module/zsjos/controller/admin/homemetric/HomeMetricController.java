package cn.iocoder.yudao.module.zsjos.controller.admin.homemetric;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import cn.iocoder.yudao.module.zsjos.service.homemetric.HomeMetricConfigService.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController @RequestMapping("/zsjos/home-metric")
public class HomeMetricController {
    @Resource private HomeMetricConfigService service;
    @Resource private HomeMetricQueryRateLimit rateLimit;
    @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    @GetMapping("/permissions") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:query')")
    public CommonResult<List<cn.iocoder.yudao.module.system.api.permission.PermissionApi.PermissionDefinition>> permissions(){return success(permissions.getDefinedPermissions());}
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:query')")
    public CommonResult<List<Config>> list(){return success(service.list());}
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:query')")
    public CommonResult<Config> get(@RequestParam Long id){return success(service.get(id));}
    @GetMapping("/catalog") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:query')")
    public CommonResult<List<HomeMetricSources.Source>> catalog(){return success(service.catalog(getLoginUserId()));}
    @PostMapping("/save") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:update')")
    public CommonResult<Config> save(@RequestBody Save request){return success(request.id()==null?service.save(request,getLoginUserId()):service.updateDraft(request,getLoginUserId()));}
    @PostMapping("/publish") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:publish')")
    public CommonResult<Config> publish(@RequestBody Command request){return success(service.publish(request,getLoginUserId()));}
    @PostMapping("/rollback") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:publish')")
    public CommonResult<Config> rollback(@RequestBody Command request){if(request.targetVersion()==null||request.targetVersion()<1)throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_ROLLBACK_INVALID);return success(service.publish(request,getLoginUserId()));}
    @PostMapping("/disable") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:publish')")
    public CommonResult<Config> disable(@RequestBody Command request){return success(service.disable(request,getLoginUserId()));}
    @PostMapping("/copy") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:update')")
    public CommonResult<Config> copy(@RequestBody Command request){return success(service.copy(request,getLoginUserId()));}
    @GetMapping("/versions") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:query')")
    public CommonResult<List<Config>> versions(@RequestParam Long id){return success(service.history(id,getLoginUserId()));}
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(mode=cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.READ_ONLY)
    @PostMapping("/preview") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:query')")
    public CommonResult<Evaluation> preview(@RequestBody HomeMetricSpec spec){rateLimit.check(getLoginUserId());return success(service.preview(spec,getLoginUserId()));}
    @GetMapping("/visible") @PreAuthorize("isAuthenticated()")
    public CommonResult<List<Config>> visible(){return success(service.visible(getLoginUserId()));}
    public record Evaluate(Long id) {}
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(mode=cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.READ_ONLY)
    @PostMapping("/evaluate") @PreAuthorize("isAuthenticated()")
    public CommonResult<Evaluated> evaluate(@RequestBody Evaluate request){rateLimit.check(getLoginUserId());return success(service.evaluate(request.id(),getLoginUserId()));}
    @GetMapping("/context") @PreAuthorize("isAuthenticated()")
    public CommonResult<ContextView> context(@RequestParam String id){rateLimit.check(getLoginUserId());return success(service.contextView(id,getLoginUserId()));}
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(mode=cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.READ_ONLY)
    @PostMapping("/details") @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<Map<String,Object>>> details(@RequestBody Details request){rateLimit.check(getLoginUserId());return success(service.details(request,getLoginUserId()));}
    public record Option(String value,String label) {}
    private List<Option> options(String... pairs){var result=new ArrayList<Option>();for(int i=0;i<pairs.length;i+=2)result.add(new Option(pairs[i],pairs[i+1]));return result;}
    @GetMapping("/options") @PreAuthorize("@ss.hasPermission('zsjos:home-metric:query')")
    public CommonResult<Map<String,List<Option>>> options(){return success(Map.of(
        "aggregates",options("COUNT","计数","DISTINCT_COUNT","去重计数","SUM","求和","AVG","平均","MIN","最小","MAX","最大"),
        "periods",options("ALL","不限时间","DAY","今日","WEEK","本周","MONTH","本月","QUARTER","本季度","YEAR","本年","CUSTOM","自定义区间"),
        "comparisons",options("CURRENT","本期","PREVIOUS","上一周期","YEAR_AGO","去年同期"),
        "actions",options("MODAL","弹窗明细","ROUTE","跳转明细页")));}
}
