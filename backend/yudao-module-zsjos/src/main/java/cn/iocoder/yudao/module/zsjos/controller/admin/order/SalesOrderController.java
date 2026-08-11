package cn.iocoder.yudao.module.zsjos.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductCatalogRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "员工工作台 - 成交订单")
@RestController
@RequestMapping("/zsjos/sales-order")
public class SalesOrderController {
    @Resource private SalesOrderService orderService;
    @Resource private ZsjosProductSkuService skuService;

    @GetMapping("/product/catalog")
    @Operation(summary = "获得全部启用成交课程 SKU")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<LeadProductCatalogRespVO> getProductCatalog() { return success(skuService.getLeadCatalog()); }

    @PostMapping("/lead/{leadId}/submit")
    @Operation(summary = "销售录入并提交成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<Long> create(@PathVariable Long leadId, @Valid @RequestBody SalesOrderSubmitReqVO reqVO) {
        return success(orderService.createAndSubmit(leadId, WebFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PutMapping("/{id}/resubmit")
    @Operation(summary = "补正并重新提交成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<Boolean> resubmit(@PathVariable Long id, @Valid @RequestBody SalesOrderSubmitReqVO reqVO) {
        orderService.reviseAndResubmit(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得成交订单详情")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:sales-order:query','zsjos:sales-order:review','zsjos:sales-order:create')")
    public CommonResult<SalesOrderRespVO> get(@PathVariable Long id) {
        return success(orderService.get(id, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/my-page")
    @Operation(summary = "获得本人提交的成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-own')")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> getMyPage(@Valid SalesOrderMyPageReqVO reqVO) {
        return success(orderService.getMyPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/my-status-counts")
    @Operation(summary = "获得本人提交的成交订单状态统计")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-own')")
    public CommonResult<SalesOrderStatusCountsRespVO> getMyStatusCounts() {
        return success(orderService.getMyStatusCounts(WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/my/{id}")
    @Operation(summary = "获得本人提交的成交订单详情")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-own')")
    public CommonResult<SalesOrderRespVO> getMyOrder(@PathVariable Long id) {
        return success(orderService.getOwn(id, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/approval/inbox-page")
    @Operation(summary = "获得成交订单会签待办或已办")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> getInboxPage(@Valid SalesOrderPageReqVO reqVO) {
        return success(orderService.getInboxPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "通过当前中心会签任务")
    public CommonResult<Boolean> approve(@PathVariable Long id, @Valid @RequestBody SalesOrderDecisionReqVO reqVO) {
        orderService.approve(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "驳回当前会签轮次")
    public CommonResult<Boolean> reject(@PathVariable Long id, @Valid @RequestBody SalesOrderDecisionReqVO reqVO) {
        orderService.reject(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PostMapping("/voucher/upload")
    @Operation(summary = "上传缴费凭证")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadVoucher(@RequestParam("file") MultipartFile file) throws IOException {
        return success(orderService.uploadVoucher(WebFrameworkUtils.getLoginUserId(), file));
    }
}
