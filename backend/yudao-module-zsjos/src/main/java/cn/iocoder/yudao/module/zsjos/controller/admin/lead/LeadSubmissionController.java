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
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submit')")
    public CommonResult<List<LeadProductSimpleRespVO>> getProductSimpleList() {
        return success(productService.getEnabledProducts());
    }

    @GetMapping("/product/catalog")
    @Operation(summary = "获得课程 SPU/SKU 目录")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:submit', 'zsjos:lead:update')")
    public CommonResult<LeadProductCatalogRespVO> getProductCatalog() {
        return success(skuService.getLeadCatalog());
    }

    @PostMapping("/attachment/upload")
    @Operation(summary = "上传客资图片")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submit')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadAttachment(@RequestParam("file") MultipartFile file)
            throws IOException {
        return success(attachmentService.upload(file));
    }

    @PostMapping("/create")
    @Operation(summary = "提交客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submit')")
    public CommonResult<LeadCreateRespVO> create(@Valid @RequestBody LeadCreateReqVO reqVO) {
        return success(submissionService.create(reqVO, getLoginUserId()));
    }
}
