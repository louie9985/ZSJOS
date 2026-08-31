package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
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
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<PageResult<WithdrawalRespVO>> page(@Valid WithdrawalPageReqVO request) {
        return success(service.getPage(request, null));
    }
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public CommonResult<WithdrawalRespVO> detail(@PathVariable Long id) {
        return success(service.getDetail(id, WebFrameworkUtils.getLoginUserId(), false));
    }
    @GetMapping("/{id}/finance-detail")
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(
            mode = cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit.Mode.SENSITIVE_READ,
            action = "withdrawal.card.view", targetType = "withdrawal")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:finance-query')")
    public CommonResult<WithdrawalRespVO> financeDetail(@PathVariable Long id) {
        return success(service.getDetail(id, WebFrameworkUtils.getLoginUserId(), true));
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
    @PostMapping("/proof/upload")
    @Operation(summary = "上传线下打款凭证")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:payout')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadProof(@RequestParam("file") MultipartFile file) throws IOException {
        return success(service.uploadProof(WebFrameworkUtils.getLoginUserId(), file));
    }
}
