package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAttachmentService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadFollowUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客资跟进")
@RestController
@RequestMapping("/zsjos/lead")
@Validated
public class LeadFollowUpController {
    @Resource private LeadFollowUpService followUpService;
    @Resource private LeadAttachmentService attachmentService;

    @GetMapping("/{id}/follow-ups/page")
    @Operation(summary = "获得客资跟进记录")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-follow-up:query')")
    @ZsjosPermission(bizType = "lead", bizId = "#id", action = "follow-up-read")
    public CommonResult<PageResult<LeadFollowUpRespVO>> getPage(
            @PathVariable("id") Long id,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") @Min(1) int pageSize) {
        return success(followUpService.getPage(id, pageNo, pageSize));
    }

    @PostMapping("/{id}/follow-ups")
    @Operation(summary = "新增客资跟进记录")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-follow-up:create')")
    @ZsjosPermission(bizType = "lead", bizId = "#id", action = "follow-up-create")
    public CommonResult<LeadFollowUpRespVO> create(@PathVariable("id") Long id,
                                                   @Valid @RequestBody LeadFollowUpCreateReqVO reqVO) {
        return success(followUpService.create(id, getLoginUserId(), reqVO));
    }

    @PostMapping("/{id}/follow-up-image/upload")
    @Operation(summary = "上传客资跟进图片")
    @PreAuthorize("@ss.hasPermission('zsjos:lead-follow-up:create')")
    @ZsjosPermission(bizType = "lead", bizId = "#id", action = "follow-up-create")
    public CommonResult<LeadAttachmentUploadRespVO> upload(@PathVariable("id") Long id,
                                                           @RequestParam("file") MultipartFile file)
            throws IOException {
        return success(attachmentService.upload(file));
    }
}
