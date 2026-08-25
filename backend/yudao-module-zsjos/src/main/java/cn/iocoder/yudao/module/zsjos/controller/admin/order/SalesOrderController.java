package cn.iocoder.yudao.module.zsjos.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
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
    @Resource private cn.iocoder.yudao.module.zsjos.service.order.SalesOrderSupervisorConfirmationService supervisorConfirmationService;

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

    @PostMapping("/lead/{leadId}/repurchase")
    @Operation(summary = "从客资详情提交系统客户复购订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<Long> createSystemRepurchase(@PathVariable Long leadId,
                                                      @Valid @RequestBody SalesOrderRepurchaseReqVO reqVO) {
        return success(orderService.createSystemRepurchase(leadId, WebFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/external-repurchase")
    @Operation(summary = "提交系统外历史客户复购订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<Long> createExternalRepurchase(@Valid @RequestBody SalesOrderRepurchaseReqVO reqVO) {
        return success(orderService.createExternalRepurchase(WebFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/student/{personId}/repurchase")
    @Operation(summary = "学习规划师为本人负责的学员提交复购订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:student-repurchase')")
    public CommonResult<Long> createStudentRepurchase(@PathVariable Long personId,
                                                       @Valid @RequestBody SalesOrderRepurchaseReqVO reqVO) {
        return success(orderService.createStudentRepurchase(personId, WebFrameworkUtils.getLoginUserId(), reqVO));
    }

    @GetMapping("/lead/{leadId}/customer-orders")
    @Operation(summary = "按客资客户聚合全部首购和复购订单")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-detail:order-read')")
    public CommonResult<java.util.List<SalesOrderListItemRespVO>> getCustomerOrders(@PathVariable Long leadId) {
        return success(orderService.getCustomerOrders(leadId, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/lead/{leadId}/customer-orders/{orderId}")
    @Operation(summary = "获得客资客户的完整订单详情")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-detail:order-read')")
    public CommonResult<SalesOrderRespVO> getCustomerOrder(@PathVariable Long leadId,
                                                            @PathVariable Long orderId) {
        return success(orderService.getCustomerOrder(leadId, orderId, WebFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/{id}/resubmit")
    @Operation(summary = "补正并重新提交成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<Long> resubmit(@PathVariable Long id, @Valid @RequestBody SalesOrderSubmitReqVO reqVO) {
        return success(orderService.reviseAndResubmit(id, WebFrameworkUtils.getLoginUserId(), reqVO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得成交订单详情")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:sales-order:query','zsjos:sales-order:query-team','zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm','zsjos:sales-order:create')")
    public CommonResult<SalesOrderRespVO> get(@PathVariable Long id) {
        return success(orderService.get(id, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/my-page")
    @Operation(summary = "获得本人提交的成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-own')")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> getMyPage(@Valid SalesOrderMyPageReqVO reqVO) {
        return success(orderService.getMyPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/my-cursor")
    @Operation(summary = "使用游标获得本人提交的成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-own')")
    public CommonResult<CursorPageResult<SalesOrderListItemRespVO>> getMyCursorPage(@Valid SalesOrderMyCursorReqVO reqVO) {
        return success(orderService.getMyCursorPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/team-page")
    @Operation(summary = "获得团队成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-team')")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> getTeamPage(@Valid SalesOrderTeamPageReqVO reqVO) {
        return success(orderService.getTeamPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/team-search-page")
    @Operation(summary = "高级筛选团队成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-team')")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> searchTeamPage(@Valid @RequestBody SalesOrderTeamPageReqVO reqVO) {
        return success(orderService.getTeamPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/team-cursor")
    @Operation(summary = "使用游标获得团队成交订单")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-team')")
    public CommonResult<CursorPageResult<SalesOrderListItemRespVO>> getTeamCursorPage(@Valid SalesOrderTeamCursorReqVO reqVO) {
        return success(orderService.getTeamCursorPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/team-search-cursor")
    @Operation(summary = "高级筛选团队成交订单游标列表")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-team')")
    public CommonResult<CursorPageResult<SalesOrderListItemRespVO>> searchTeamCursorPage(@Valid @RequestBody SalesOrderTeamCursorReqVO reqVO) {
        return success(orderService.getTeamCursorPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/team-status-counts")
    @Operation(summary = "获得团队成交订单状态统计")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-team')")
    public CommonResult<SalesOrderStatusCountsRespVO> getTeamStatusCounts() {
        return success(orderService.getTeamStatusCounts(WebFrameworkUtils.getLoginUserId()));
    }
    @PostMapping("/my-search-cursor")
    @Operation(summary = "高级筛选本人订单游标列表")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-own')")
    public CommonResult<CursorPageResult<SalesOrderListItemRespVO>> searchMyCursorPage(
            @Valid @RequestBody SalesOrderMyCursorReqVO reqVO) {
        return success(orderService.getMyCursorPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }
    @PostMapping("/my-search-page")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:query-own')")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> searchMyPage(@Valid @RequestBody SalesOrderMyPageReqVO reqVO) {
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
    @Operation(summary = "获得成交订单审批待办或已办")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> getInboxPage(@Valid SalesOrderPageReqVO reqVO) {
        return success(orderService.getInboxPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/approval/inbox-cursor")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<CursorPageResult<SalesOrderListItemRespVO>> getInboxCursor(@Valid SalesOrderPageReqVO reqVO) {
        return success(orderService.getInboxCursor(reqVO, WebFrameworkUtils.getLoginUserId()));
    }
    @PostMapping("/approval/search-page")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<PageResult<SalesOrderListItemRespVO>> searchInboxPage(@Valid @RequestBody SalesOrderPageReqVO reqVO) {
        return success(orderService.getInboxPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }
    @PostMapping("/approval/search-cursor")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<CursorPageResult<SalesOrderListItemRespVO>> searchInboxCursor(@Valid @RequestBody SalesOrderPageReqVO reqVO) {
        return success(orderService.getInboxCursor(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/approval/filter-profile")
    @Operation(summary = "获得成交审批筛选方案")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<SalesOrderApprovalFilterProfileRespVO> getApprovalFilterProfile() {
        return success(orderService.getApprovalFilterProfile(WebFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "通过当前中心审批任务")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<Boolean> approve(@PathVariable Long id, @Valid @RequestBody SalesOrderDecisionReqVO reqVO) {
        orderService.approve(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "驳回当前审批轮次")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<Boolean> reject(@PathVariable Long id, @Valid @RequestBody SalesOrderDecisionReqVO reqVO) {
        orderService.reject(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PutMapping("/{id}/supervisor-confirmation/request")
    @Operation(summary = "申请订单销售主管确认")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:review')")
    public CommonResult<Boolean> requestSupervisorConfirmation(@PathVariable Long id,
                                                                @Valid @RequestBody SalesOrderSupervisorRequestReqVO reqVO) {
        supervisorConfirmationService.request(id, WebFrameworkUtils.getLoginUserId(), reqVO);
        return success(true);
    }

    @GetMapping("/supervisor-confirmation/inbox-page")
    @Operation(summary = "获得主管确认待办或已办")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:supervisor-confirm')")
    public CommonResult<PageResult<SalesOrderSupervisorConfirmationRespVO>> getSupervisorConfirmationInbox(
            @Valid SalesOrderSupervisorPageReqVO reqVO) {
        return success(supervisorConfirmationService.getInboxPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/supervisor-confirmation/{confirmationId}")
    @Operation(summary = "获得本人主管确认记录")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:supervisor-confirm')")
    public CommonResult<SalesOrderSupervisorConfirmationRespVO> getSupervisorConfirmation(@PathVariable Long confirmationId) {
        return success(supervisorConfirmationService.getConfirmation(confirmationId, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/approval/task-target")
    @Operation(summary = "定位当前用户的成交审批任务")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm')")
    public CommonResult<SalesOrderApprovalTaskTargetRespVO> getApprovalTaskTarget(@RequestParam String taskId) {
        return success(supervisorConfirmationService.getTaskTarget(taskId, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/approval/notification-target")
    @Operation(summary = "定位当前用户收到的成交审批通知")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:sales-order:review','zsjos:sales-order:supervisor-confirm')")
    public CommonResult<SalesOrderApprovalTaskTargetRespVO> getApprovalNotificationTarget(
            @RequestParam Long orderId, @RequestParam String sceneCode,
            @RequestParam(required = false) String sourceEventKey) {
        return success(supervisorConfirmationService.getNotificationTarget(
                orderId, sceneCode, sourceEventKey, WebFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/supervisor-confirmation/inbox-cursor")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:supervisor-confirm')")
    public CommonResult<CursorPageResult<SalesOrderSupervisorConfirmationRespVO>> getSupervisorConfirmationCursor(
            @Valid SalesOrderSupervisorCursorReqVO reqVO) {
        return success(supervisorConfirmationService.getInboxCursor(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/supervisor-confirmation/search-page")
    @Operation(summary = "搜索主管确认待办或已办")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:supervisor-confirm')")
    public CommonResult<PageResult<SalesOrderSupervisorConfirmationRespVO>> searchSupervisorConfirmationInbox(
            @Valid @RequestBody SalesOrderSupervisorPageReqVO reqVO) {
        return success(supervisorConfirmationService.getInboxPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/supervisor-confirmation/search-cursor")
    @Operation(summary = "游标搜索主管确认待办或已办")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:supervisor-confirm')")
    public CommonResult<CursorPageResult<SalesOrderSupervisorConfirmationRespVO>> searchSupervisorConfirmationCursor(
            @Valid @RequestBody SalesOrderSupervisorCursorReqVO reqVO) {
        return success(supervisorConfirmationService.getInboxCursor(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/{id}/supervisor-confirmation/confirm")
    @Operation(summary = "主管通过成交订单审批")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:supervisor-confirm')")
    public CommonResult<Boolean> confirmSupervisorConfirmation(@PathVariable Long id,
                                                                @Valid @RequestBody SalesOrderSupervisorDecisionReqVO reqVO) {
        supervisorConfirmationService.decide(id, WebFrameworkUtils.getLoginUserId(), reqVO, true);
        return success(true);
    }

    @PutMapping("/{id}/supervisor-confirmation/reject")
    @Operation(summary = "主管驳回成交订单审批")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:supervisor-confirm')")
    public CommonResult<Boolean> rejectSupervisorConfirmation(@PathVariable Long id,
                                                               @Valid @RequestBody SalesOrderSupervisorDecisionReqVO reqVO) {
        supervisorConfirmationService.decide(id, WebFrameworkUtils.getLoginUserId(), reqVO, false);
        return success(true);
    }

    @PutMapping("/{id}/terminate")
    @Operation(summary = "订单创建人终止当前审批")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<Boolean> terminate(@PathVariable Long id, @Valid @RequestBody SalesOrderTerminateReqVO reqVO) {
        orderService.terminate(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PostMapping("/voucher/upload")
    @Operation(summary = "上传缴费凭证")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadVoucher(@RequestParam("file") MultipartFile file) throws IOException {
        return success(orderService.uploadVoucher(WebFrameworkUtils.getLoginUserId(), file));
    }
}
