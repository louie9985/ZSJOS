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
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** HTTP facade for the independent partner frontend. */
@RestController
@RequestMapping("/zsjos")
@PreAuthorize("@ss.hasRole('part_time_partner')")
public class PartnerAppPortalController {
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
    @PreAuthorize("@ss.hasPermission('zsjos:partner:self-query')")
    public CommonResult<PartnerMeRespVO> me() { return success(partnerService.getMe(getLoginUserId())); }

    @GetMapping("/lead/product/catalog")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submit')")
    public CommonResult<LeadProductCatalogRespVO> catalog() { return success(skuService.getLeadCatalog()); }

    @PostMapping("/lead/attachment/upload")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submit')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadLeadAttachment(@RequestParam("file") MultipartFile file)
            throws IOException { return success(attachmentService.upload(file)); }

    @PostMapping("/lead/create")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submit')")
    public CommonResult<LeadCreateRespVO> createLead(@Valid @RequestBody LeadCreateReqVO request) {
        return success(submissionService.create(request, getLoginUserId()));
    }

    @GetMapping("/lead/inbox/submitted/page")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query-submitted')")
    public CommonResult<PageResult<LeadManagementRespVO>> submitted(@Valid LeadManagementPageReqVO request) {
        request.setAudience("submitter");
        return success(leadManagementService.getLeadPage(request, getLoginUserId()));
    }

    @GetMapping("/lead/get")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query-submitted')")
    public CommonResult<LeadManagementRespVO> lead(@RequestParam Long id) {
        return success(leadManagementService.getLead(id, getLoginUserId()));
    }

    @PutMapping("/lead/{id}/submitter-supplement")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submitter-supplement')")
    public CommonResult<Boolean> supplement(@PathVariable Long id,
                                             @Valid @RequestBody LeadSubmitterSupplementReqVO request) {
        submitterActionService.supplement(id, getLoginUserId(), request); return success(true);
    }

    @PostMapping("/lead/{id}/urge")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:urge')")
    public CommonResult<Boolean> urge(@PathVariable Long id, @Valid @RequestBody LeadUrgeReqVO request) {
        submitterActionService.urge(id, getLoginUserId(), request); return success(true);
    }

    @GetMapping("/cashback/my-summary")
    @PreAuthorize("@ss.hasPermission('zsjos:cashback:my-query')")
    public CommonResult<CashbackSummaryRespVO> cashbackSummary() {
        return success(cashbackService.getMySummary(getLoginUserId()));
    }

    @GetMapping("/withdrawal/my-summary")
    @PreAuthorize("@ss.hasPermission('zsjos:withdrawal:apply')")
    public CommonResult<WithdrawalSummaryRespVO> withdrawalSummary() {
        return success(withdrawalService.getMySummary(getLoginUserId()));
    }
}
