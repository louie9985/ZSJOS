package cn.iocoder.yudao.module.zsjos.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.service.order.OrderProfileService;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/sales-order/profile")
public class OrderProfileController {
    @Resource private OrderProfileService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.lead.PersonMergeIdentityService mergedIdentity;
    @GetMapping("/lead/{id}")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<OrderProfileRespVO> lead(@PathVariable Long id) { return success(service.forLead(id)); }
    @GetMapping("/order/{id}")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<OrderProfileRespVO> order(@PathVariable Long id) { return success(service.forOrder(id)); }
    @GetMapping("/student/{id}")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:student-repurchase')")
    public CommonResult<OrderProfileRespVO> student(@PathVariable Long id) { return success(service.forStudent(mergedIdentity.resolve(id))); }
    @PostMapping("/customer")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<OrderProfileRespVO> customer(@Valid @RequestBody RepurchaseCustomerCheckReqVO req) {
        return success(service.forCustomer(WebFrameworkUtils.getLoginUserId(),req));
    }
}
