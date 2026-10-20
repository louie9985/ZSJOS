package cn.iocoder.yudao.module.zsjos.controller.admin.performance;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryPerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.service.deliveryperformance.*;
import cn.iocoder.yudao.framework.common.pojo.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @Validated @RequestMapping("/zsjos/delivery-performance")
@PreAuthorize("@ss.hasPermission('zsjos:delivery-performance:query')")
public class DeliveryPerformanceController {
 @Resource private DeliveryPerformanceAccess access;
 @Resource private DeliveryPerformanceStatisticsService service;
 @GetMapping("/unclassified-history") @PreAuthorize("@ss.hasPermission('zsjos:delivery-performance:detail')") public CommonResult<PageResult<UnclassifiedDetail>> unclassified(@Valid UnclassifiedQuery q){return success(service.unclassified(q));}
 @GetMapping("/tree") public CommonResult<List<Node>> tree(){return success(access.tree(false));}
 @GetMapping("/overview") public CommonResult<Overview> overview(@Valid Query q){return success(service.overview(q));}
 @GetMapping("/analysis") public CommonResult<Analysis> analysis(@Valid Query q){return success(service.analysis(q));}
 @GetMapping("/history") public CommonResult<List<HistoryMonth>> history(@Valid Query q){return success(service.history(q));}
 @GetMapping("/leads") public CommonResult<LeadReport> leads(@Valid Query q){return success(service.leads(q));}
 @GetMapping("/lead-workload") public CommonResult<LeadWorkload> leadWorkload(@Valid Query q){return success(service.leadWorkload(q));}
 @GetMapping("/lead-calendar") public CommonResult<LeadCalendar> leadCalendar(@Valid Query q){return success(service.leadCalendar(q));}
 @GetMapping("/missing-targets") public CommonResult<List<MissingTarget>> missingTargets(@Valid Query q){return success(service.missingTargets(q));}
 @GetMapping("/details") public CommonResult<PageResult<Detail>> details(@Valid Query q){return success(service.details(q));}
 @GetMapping("/order-details") @PreAuthorize("@ss.hasPermission('zsjos:delivery-performance:detail')") public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryOrderDetailVO.Response> orderDetails(
   @Valid cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryOrderDetailVO.Query q){return success(service.orderDetails(q));}
 @GetMapping("/conversion-details") @PreAuthorize("@ss.hasPermission('zsjos:delivery-performance:detail')") public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryConversionDetailVO.Response> conversionDetails(
   @Valid cn.iocoder.yudao.module.zsjos.controller.admin.deliveryperformance.vo.DeliveryConversionDetailVO.Query q){return success(service.conversionDetails(q));}
}
