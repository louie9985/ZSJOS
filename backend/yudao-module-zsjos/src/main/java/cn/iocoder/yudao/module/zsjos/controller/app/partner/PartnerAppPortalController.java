package cn.iocoder.yudao.module.zsjos.controller.app.partner;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterSupplementReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadUrgeReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.*;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerManagementService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** HTTP facade for the independent partner frontend. */
@RestController
@RequestMapping("/zsjos")
public class PartnerAppPortalController {
    @Resource private PartnerAccountService partnerAccountService;
    @Resource private PartnerManagementService partnerService;
    @Resource private LeadSubmissionService submissionService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadProductService productService;
    @Resource private ZsjosProductSkuService skuService;
    @Resource private LeadManagementService leadManagementService;
    @Resource private LeadSubmitterActionService submitterActionService;
    @Resource private LeadComplaintService complaintService;
    @Resource private LeadAppealService appealService;
    @Resource private CashbackService cashbackService;
    @Resource private WithdrawalService withdrawalService;

    @GetMapping("/partner/me")
    public CommonResult<PartnerMeRespVO> me() { return success(partnerService.getMe(getLoginUserId())); }

    @GetMapping("/lead/product/catalog")
    public CommonResult<LeadProductCatalogRespVO> catalog() { partnerId(); return success(skuService.getLeadCatalog()); }

    @PostMapping("/lead/attachment/upload")
    public CommonResult<LeadAttachmentUploadRespVO> uploadLeadAttachment(@RequestParam("file") MultipartFile file)
            throws IOException {
        partnerId();
        return success(attachmentService.uploadForPartner(file, getLoginUserId()));
    }

    @PostMapping("/lead/create")
    public CommonResult<LeadCreateRespVO> createLead(@Valid @RequestBody LeadCreateReqVO request) {
        return success(submissionService.createForPartner(request, getLoginUserId(), partnerId()));
    }

    @GetMapping("/lead/inbox/submitted/page")
    public CommonResult<PageResult<LeadManagementRespVO>> submitted(@Valid LeadManagementPageReqVO request) {
        request.setAudience("submitter");
        return success(leadManagementService.getPartnerLeadPage(request, partnerId()));
    }

    @GetMapping("/lead/inbox/submitted/summary")
    public CommonResult<PartnerLeadFollowUpSummaryRespVO> submittedSummary() {
        return success(leadManagementService.getPartnerLeadFollowUpSummary(partnerId()));
    }

    @GetMapping("/lead/get")
    public CommonResult<LeadManagementRespVO> lead(@RequestParam Long id) {
        return success(leadManagementService.getPartnerLead(id, partnerId()));
    }

    @PutMapping("/lead/{id}/submitter-supplement")
    public CommonResult<Boolean> supplement(@PathVariable Long id,
                                             @Valid @RequestBody LeadSubmitterSupplementReqVO request) {
        submitterActionService.supplementForPartner(id, partnerId(), request); return success(true);
    }

    @PostMapping("/lead/{id}/urge")
    public CommonResult<Boolean> urge(@PathVariable Long id, @Valid @RequestBody LeadUrgeReqVO request) {
        submitterActionService.urgeForPartner(id, partnerId(), request); return success(true);
    }

    @GetMapping("/cashback/my-summary")
    public CommonResult<CashbackSummaryRespVO> cashbackSummary() {
        return success(cashbackService.getPartnerSummary(partnerId()));
    }

    @GetMapping("/withdrawal/my-summary")
    public CommonResult<WithdrawalSummaryRespVO> withdrawalSummary() {
        return success(withdrawalService.getPartnerSummary(partnerId()));
    }

    private Long partnerId() { return partnerAccountService.requireContext(getLoginUserId()).partnerId(); }
}
