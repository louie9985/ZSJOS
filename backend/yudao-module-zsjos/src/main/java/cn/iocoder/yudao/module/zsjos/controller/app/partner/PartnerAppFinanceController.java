package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.complaint.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.BankCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.BankCardUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadComplaintService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAppealService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAttachmentService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos")
public class PartnerAppFinanceController {
    @Resource private PartnerAccountService partnerAccountService;
    @Resource private CashbackService cashbackService;
    @Resource private WithdrawalService withdrawalService;
    @Resource private LeadComplaintService complaintService;
    @Resource private LeadAppealService appealService;
    @Resource private LeadAttachmentService attachmentService;

    @GetMapping("/cashback/my-page")
    public CommonResult<PageResult<CashbackRespVO>> cashbackPage(@Valid CashbackPageReqVO request) {
        return success(cashbackService.getPartnerPage(request, partnerId()));
    }

    @GetMapping("/withdrawal/my-cards")
    public CommonResult<List<BankCardRespVO>> cards() { return success(withdrawalService.getPartnerCards(partnerId())); }

    @PostMapping("/withdrawal/my-cards")
    public CommonResult<Long> saveCard(@Valid @RequestBody BankCardSaveReqVO request) {
        return success(withdrawalService.savePartnerCard(partnerId(), request));
    }

    @PutMapping("/withdrawal/my-cards/{id}")
    public CommonResult<Boolean> updateCard(@PathVariable Long id,
                                            @Valid @RequestBody BankCardUpdateReqVO request) {
        withdrawalService.updatePartnerCard(partnerId(), id, request);
        return success(true);
    }

    @DeleteMapping("/withdrawal/my-cards/{id}")
    public CommonResult<Boolean> deleteCard(@PathVariable Long id) {
        withdrawalService.deletePartnerCard(partnerId(), id); return success(true);
    }

    @PutMapping("/withdrawal/my-cards/{id}/default")
    public CommonResult<Boolean> defaultCard(@PathVariable Long id) {
        withdrawalService.setPartnerDefaultCard(partnerId(), id); return success(true);
    }

    @PostMapping("/withdrawal/apply")
    public CommonResult<Long> apply(@Valid @RequestBody WithdrawalApplyReqVO request) {
        return success(withdrawalService.applyForPartner(getLoginUserId(), partnerId(), request));
    }

    @PutMapping("/withdrawal/{id}/cancel")
    public CommonResult<Boolean> cancel(@PathVariable Long id) {
        withdrawalService.cancelForPartner(id, getLoginUserId(), partnerId()); return success(true);
    }

    @GetMapping("/withdrawal/my-page")
    public CommonResult<PageResult<PartnerWithdrawalRespVO>> withdrawalPage(@Valid WithdrawalPageReqVO request) {
        return success(withdrawalService.getPartnerPage(request, partnerId()));
    }

    @GetMapping("/withdrawal/my/{id}")
    public CommonResult<PartnerWithdrawalRespVO> withdrawal(@PathVariable Long id) {
        return success(withdrawalService.getPartnerDetail(id, partnerId()));
    }

    @PostMapping("/lead-complaint/lead/{leadId}")
    public CommonResult<Long> createComplaint(@PathVariable Long leadId,
                                                @Valid @RequestBody LeadComplaintCreateReqVO request) {
        return success(complaintService.createForPartner(leadId, getLoginUserId(), partnerId(), request));
    }

    @GetMapping("/lead-complaint/my-page")
    public CommonResult<PageResult<LeadComplaintRespVO>> myComplaints(@Valid LeadComplaintPageReqVO request) {
        return success(complaintService.partnerPage(request, partnerId()));
    }

    @GetMapping("/lead/appeal/lead/{leadId}/list")
    public CommonResult<List<LeadAppealRespVO>> appeals(@PathVariable Long leadId) {
        return success(appealService.getPartnerLeadAppeals(leadId, partnerId()));
    }

    @PostMapping("/lead/appeal/lead/{leadId}/submit")
    public CommonResult<Long> submitAppeal(@PathVariable Long leadId,
                                            @Valid @RequestBody LeadAppealSubmitReqVO request) {
        return success(appealService.submitForPartner(leadId, getLoginUserId(), partnerId(), request));
    }

    @PostMapping("/lead/appeal/attachment/upload")
    public CommonResult<LeadAttachmentUploadRespVO> uploadAppeal(@RequestParam("file") MultipartFile file)
            throws IOException {
        partnerId();
        return success(appealService.uploadForPartner(file, getLoginUserId()));
    }

    private Long partnerId() { return partnerAccountService.requireContext(getLoginUserId()).partnerId(); }
}
