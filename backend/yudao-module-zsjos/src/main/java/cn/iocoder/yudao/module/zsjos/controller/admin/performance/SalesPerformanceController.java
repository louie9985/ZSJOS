package cn.iocoder.yudao.module.zsjos.controller.admin.performance;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.service.performance.*;
import cn.iocoder.yudao.framework.common.pojo.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @Validated @RequestMapping("/zsjos/sales-performance")
@PreAuthorize("@ss.hasPermission('zsjos:sales-performance:query')")
public class SalesPerformanceController {
 @Resource private PerformanceAccess access;
 @Resource private PerformanceStatisticsService service;
 @GetMapping("/tree") public CommonResult<List<Node>> tree(){return success(access.tree(false));}
 @GetMapping("/overview") public CommonResult<Overview> overview(@Valid Query q){return success(service.overview(q));}
 @GetMapping("/analysis") public CommonResult<Analysis> analysis(@Valid Query q){return success(service.analysis(q));}
 @GetMapping("/history") public CommonResult<List<HistoryMonth>> history(@Valid Query q){return success(service.history(q));}
 @GetMapping("/leads") public CommonResult<LeadReport> leads(@Valid Query q){return success(service.leads(q));}
 @GetMapping("/details") public CommonResult<PageResult<Detail>> details(@Valid Query q){return success(service.details(q));}
}
