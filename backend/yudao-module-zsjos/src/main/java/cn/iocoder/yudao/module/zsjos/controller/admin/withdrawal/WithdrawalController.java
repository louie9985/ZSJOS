package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalBatchPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "中世健 - 兼职提现与线下打款")
@RestController
@RequestMapping("/zsjos/withdrawal")
public class WithdrawalController {
    @Resource private WithdrawalService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.cashback.FinanceTraceService traceService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalReviewService reviewService;
    @Resource private WithdrawalBatchPayoutService batchPayoutService;

    @PostMapping("/search-page")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(
            mode = cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.SENSITIVE_READ,
            action = "withdrawal.management.list", targetType = "withdrawal")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<PageResult<WithdrawalRespVO>> searchPage(@Valid @RequestBody WithdrawalPageReqVO request) {
        return success(traceService.enrichWithdrawalPage(service.getManagementPage(request)));
    }

    @PostMapping("/my-search-page")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(mode = cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:my-query')")
    public CommonResult<PageResult<WithdrawalRespVO>> mySearchPage(@Valid @RequestBody WithdrawalPageReqVO request) {
        return success(service.getPage(request, WebFrameworkUtils.getLoginUserId()));
    }



    @PostMapping("/apply")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<Long> apply(@Valid @RequestBody WithdrawalApplyReqVO request) {
        return success(service.apply(WebFrameworkUtils.getLoginUserId(), request));
    }
    @PutMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<Boolean> cancel(@PathVariable Long id) {
        service.cancel(id, WebFrameworkUtils.getLoginUserId()); return success(true);
    }
    @GetMapping("/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:my-query')")
    public CommonResult<PageResult<WithdrawalRespVO>> myPage(@Valid WithdrawalPageReqVO request) {
        return success(service.getPage(request, WebFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/my/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:my-query')")
    public CommonResult<WithdrawalRespVO> myDetail(@PathVariable Long id) {
        return success(service.getDetail(id, WebFrameworkUtils.getLoginUserId(), false));
    }
    @GetMapping("/my-cards")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<List<BankCardRespVO>> myCards() {
        return success(service.getMyCards(WebFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/page")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(
            mode = cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.SENSITIVE_READ,
            action = "withdrawal.management.list", targetType = "withdrawal")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<PageResult<WithdrawalRespVO>> page(@Valid WithdrawalPageReqVO request) {
        return success(traceService.enrichWithdrawalPage(service.getManagementPage(request)));
    }
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<WithdrawalRespVO> detail(@PathVariable Long id) {
        return success(traceService.enrichWithdrawal(reviewService.enrich(service.getDetail(id, WebFrameworkUtils.getLoginUserId(), false), WebFrameworkUtils.getLoginUserId())));
    }
    @GetMapping("/{id}/sources")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<PageResult<WithdrawalSourceRespVO>> sources(@PathVariable Long id,
            @Valid cn.iocoder.yudao.framework.common.pojo.PageParam page) {
        return success(traceService.sources(id, page));
    }
    @GetMapping("/{id}/finance-detail")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(
            mode = cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.SENSITIVE_READ,
            action = "withdrawal.card.view", targetType = "withdrawal")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query','zsjos:withdrawal:my-query')")
    public CommonResult<WithdrawalRespVO> financeDetail(@PathVariable Long id) {
        return success(traceService.enrichWithdrawalIfManagement(reviewService.enrich(service.getDetail(id, WebFrameworkUtils.getLoginUserId(), true), WebFrameworkUtils.getLoginUserId())));
    }
    @PutMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:review') && @ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<Boolean> approve(@PathVariable Long id, @Valid @RequestBody WithdrawalReviewReqVO request) {
        reviewService.decide(id, WebFrameworkUtils.getLoginUserId(), request, true);
        return success(true);
    }
    @PutMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:review') && @ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<Boolean> reject(@PathVariable Long id, @Valid @RequestBody WithdrawalReviewReqVO request) {
        reviewService.decide(id, WebFrameworkUtils.getLoginUserId(), request, false);
        return success(true);
    }
    @GetMapping("/{id}/compatibility")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:review') && @ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<java.util.Map<String, Object>> compatibility(@PathVariable Long id) {
        return success(reviewService.compatibility(id));
    }
    @PutMapping("/{id}/sync-process-result")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:review') && @ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<Boolean> syncProcessResult(@PathVariable Long id, @Valid @RequestBody WithdrawalReviewReqVO request) {
        reviewService.repair(id, request.getVersion());
        return success(true);
    }
    @PutMapping("/{id}/reject-approved")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:review')")
    public CommonResult<Boolean> rejectApproved(@PathVariable Long id,
                                                 @Valid @RequestBody WithdrawalRejectReqVO request) {
        service.rejectApproved(id, WebFrameworkUtils.getLoginUserId(), request.getReason()); return success(true);
    }
    @PutMapping("/{id}/payout")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:payout')")
    public CommonResult<Boolean> payout(@PathVariable Long id, @Valid @RequestBody WithdrawalPayoutReqVO request) {
        service.recordPayout(id, WebFrameworkUtils.getLoginUserId(), request); return success(true);
    }
    @PutMapping("/batch-payout")
    @Operation(summary = "批量登记打款")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:payout')")
    public CommonResult<Boolean> batchPayout(@Valid @RequestBody WithdrawalBatchPayoutReqVO request) {
        batchPayoutService.recordPayouts(WebFrameworkUtils.getLoginUserId(), request);
        return success(true);
    }
    @PostMapping("/proof/upload")
    @Operation(summary = "上传线下打款凭证")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:payout')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadProof(@RequestParam("file") MultipartFile file) throws IOException {
        return success(service.uploadProof(WebFrameworkUtils.getLoginUserId(), file));
    }
}
