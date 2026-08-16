package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.BankCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadComplaintService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAppealService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAttachmentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos")
@PreAuthorize("@ss.hasRole('part_time_partner')")
public class PartnerAppFinanceController {
    @Resource private CashbackService cashbackService;
    @Resource private WithdrawalService withdrawalService;
    @Resource private LeadComplaintService complaintService;
    @Resource private LeadAppealService appealService;
    @Resource private LeadAttachmentService attachmentService;

    @GetMapping("/cashback/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:my-query')")
    public CommonResult<PageResult<CashbackRespVO>> cashbackPage(@Valid CashbackPageReqVO request) {
        return success(cashbackService.getPage(request, getLoginUserId()));
    }

    @GetMapping("/withdrawal/my-cards")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<List<BankCardRespVO>> cards() { return success(withdrawalService.getMyCards(getLoginUserId())); }

    @PostMapping("/withdrawal/my-cards")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<Long> saveCard(@Valid @RequestBody BankCardSaveReqVO request) {
        return success(withdrawalService.saveMyCard(getLoginUserId(), request));
    }

    @DeleteMapping("/withdrawal/my-cards/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<Boolean> deleteCard(@PathVariable Long id) {
        withdrawalService.deleteMyCard(getLoginUserId(), id); return success(true);
    }

    @PutMapping("/withdrawal/my-cards/{id}/default")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<Boolean> defaultCard(@PathVariable Long id) {
        withdrawalService.setDefaultCard(getLoginUserId(), id); return success(true);
    }

    @PostMapping("/withdrawal/apply")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<Long> apply(@Valid @RequestBody WithdrawalApplyReqVO request) {
        return success(withdrawalService.apply(getLoginUserId(), request));
    }

    @PutMapping("/withdrawal/{id}/cancel")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<Boolean> cancel(@PathVariable Long id) {
        withdrawalService.cancel(id, getLoginUserId()); return success(true);
    }

    @GetMapping("/withdrawal/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:my-query')")
    public CommonResult<PageResult<WithdrawalRespVO>> withdrawalPage(@Valid WithdrawalPageReqVO request) {
        return success(withdrawalService.getPage(request, getLoginUserId()));
    }

    @GetMapping("/withdrawal/my/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:my-query')")
    public CommonResult<WithdrawalRespVO> withdrawal(@PathVariable Long id) {
        return success(withdrawalService.getDetail(id, getLoginUserId(), false));
    }

    @PostMapping("/lead-complaint/lead/{leadId}")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-complaint:create')")
    public CommonResult<Long> createComplaint(@PathVariable Long leadId,
                                                @Valid @RequestBody LeadComplaintCreateReqVO request) {
        return success(complaintService.create(leadId, getLoginUserId(), request));
    }

    @GetMapping("/lead-complaint/my-page")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-complaint:create')")
    public CommonResult<PageResult<LeadComplaintRespVO>> myComplaints(@Valid LeadComplaintPageReqVO request) {
        return success(complaintService.myPage(request, getLoginUserId()));
    }

    @GetMapping("/lead/appeal/lead/{leadId}/list")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:appeal:create')")
    public CommonResult<List<LeadAppealRespVO>> appeals(@PathVariable Long leadId) {
        return success(appealService.getLeadAppeals(leadId, getLoginUserId()));
    }

    @PostMapping("/lead/appeal/lead/{leadId}/submit")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:appeal:create')")
    public CommonResult<Long> submitAppeal(@PathVariable Long leadId,
                                            @Valid @RequestBody LeadAppealSubmitReqVO request) {
        return success(appealService.submit(leadId, getLoginUserId(), request));
    }

    @PostMapping("/lead/appeal/attachment/upload")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:appeal:create')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadAppeal(@RequestParam("file") MultipartFile file)
            throws IOException { return success(appealService.upload(file)); }
}
