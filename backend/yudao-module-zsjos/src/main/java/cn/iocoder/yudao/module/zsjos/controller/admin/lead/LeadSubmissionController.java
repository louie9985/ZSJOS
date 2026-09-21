package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAttachmentService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadProductService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmissionService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客资提交")
@RestController
@RequestMapping("/zsjos/lead")
public class LeadSubmissionController {
    @Resource private LeadSubmissionService submissionService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadProductService productService;
    @Resource private ZsjosProductSkuService skuService;

    @GetMapping("/product/simple-list")
    @Operation(summary = "获得启用课程列表")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:self-sourced:create', 'zsjos:lead:education-self-sourced:create', 'zsjos:lead:submitter-supplement')")
    public CommonResult<List<LeadProductSimpleRespVO>> getProductSimpleList() {
        return success(productService.getEnabledProducts());
    }

    @GetMapping("/product/catalog")
    @Operation(summary = "获得课程 SPU/SKU 目录")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:self-sourced:create', 'zsjos:lead:education-self-sourced:create', 'zsjos:lead:update', 'zsjos:lead:submitter-supplement')")
    public CommonResult<LeadProductCatalogRespVO> getProductCatalog() {
        return success(skuService.getLeadCatalog());
    }

    @PostMapping("/attachment/upload")
    @Operation(summary = "上传客资图片")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:self-sourced:create', 'zsjos:lead:education-self-sourced:create', 'zsjos:lead:submitter-supplement', 'zsjos:lead-complaint:create', 'zsjos:lead-complaint:handle', 'zsjos:lead:request-submitter-assist')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadAttachment(@RequestParam("file") MultipartFile file)
            throws IOException {
        return success(attachmentService.upload(file));
    }

    @PostMapping("/contact-check")
    @Operation(summary = "联系方式查重并激活已有客资")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:self-sourced:create', 'zsjos:lead:education-self-sourced:create')")
    public CommonResult<Boolean> checkContact(@Valid @RequestBody LeadContactCheckReqVO reqVO) {
        return success(submissionService.checkContact(reqVO, getLoginUserId()));
    }

    @PostMapping("/self-sourced/contact-check")
    @Operation(summary = "销售自拓联系方式查重并激活已有客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:self-sourced:create')")
    public CommonResult<Boolean> checkSelfSourcedContact(@Valid @RequestBody LeadContactCheckReqVO reqVO) {
        return success(submissionService.checkContact(reqVO, getLoginUserId(),
                cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmissionIdentityService.Identity.SALES));
    }

    @PostMapping("/education-self-sourced/contact-check")
    @Operation(summary = "教务自拓联系方式查重并激活已有客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:education-self-sourced:create')")
    public CommonResult<Boolean> checkEducationSelfSourcedContact(@Valid @RequestBody LeadContactCheckReqVO reqVO) {
        return success(submissionService.checkContact(reqVO, getLoginUserId(),
                cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmissionIdentityService.Identity.EDUCATION));
    }

    @PostMapping("/create")
    @Operation(summary = "提交客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submit')")
    public CommonResult<LeadCreateRespVO> create(@Valid @RequestBody LeadCreateReqVO reqVO) {
        return success(submissionService.create(reqVO, getLoginUserId()));
    }

    @PostMapping("/self-sourced/create")
    @Operation(summary = "销售提交自拓客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:self-sourced:create')")
    public CommonResult<LeadCreateRespVO> createSelfSourced(@Valid @RequestBody LeadCreateReqVO reqVO) {
        return success(submissionService.createSelfSourced(reqVO, getLoginUserId()));
    }

    @PostMapping("/education-self-sourced/create")
    @Operation(summary = "教务提交自拓客资并直接归属本人")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:education-self-sourced:create')")
    public CommonResult<LeadCreateRespVO> createEducationSelfSourced(@Valid @RequestBody LeadCreateReqVO reqVO) {
        return success(submissionService.createEducationSelfSourced(reqVO, getLoginUserId()));
    }

    @GetMapping("/self-sourced/new-media-providers")
    @Operation(summary = "获得自拓可选的新媒体提供方")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:self-sourced:create', 'zsjos:lead:education-self-sourced:create')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getNewMediaProviders() {
        return success(submissionService.getNewMediaProviders());
    }
}
