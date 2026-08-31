package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadDuplicateReviewService;
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
@RequestMapping("/zsjos/lead-duplicate-review")
public class LeadDuplicateReviewController {
    @Resource private LeadDuplicateReviewService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.lead.LeadAttachmentService attachmentService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-duplicate-review:query')")
    public CommonResult<PageResult<LeadDuplicateReviewRespVO>> page(@Valid LeadDuplicateReviewPageReqVO request) {
        return success(service.getPage(request));
    }

    @PostMapping("/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead-duplicate-review:query')")
    public CommonResult<PageResult<LeadDuplicateReviewRespVO>> searchPage(
            @Valid @RequestBody LeadDuplicateReviewPageReqVO request) {
        return success(service.getPage(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-duplicate-review:query')")
    public CommonResult<LeadDuplicateReviewRespVO> get(@PathVariable Long id) { return success(service.get(id)); }

    @GetMapping("/sales-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-duplicate-review:process')")
    public CommonResult<List<LeadAssignmentUserRespVO>> salesCandidates() {
        return success(service.getSalesCandidates(getLoginUserId()));
    }

    @PostMapping("/attachment/upload")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-duplicate-review:process')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO>
    uploadAttachment(@RequestParam("file") MultipartFile file) throws IOException {
        return success(attachmentService.upload(file));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-duplicate-review:process')")
    public CommonResult<Boolean> decide(@PathVariable Long id,
                                         @Valid @RequestBody LeadDuplicateReviewDecisionReqVO request) {
        service.decide(id, request, getLoginUserId());
        return success(true);
    }
}
