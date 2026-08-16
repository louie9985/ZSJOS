package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAppealService;
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

@Tag(name = "管理后台 - 客资申诉")
@RestController
@RequestMapping("/zsjos/lead/appeal")
public class LeadAppealController {
    @Resource private LeadAppealService appealService;

    @GetMapping("/lead/{leadId}/list")
    @Operation(summary = "获得客资申诉记录")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:appeal:create','zsjos:lead:appeal:query')")
    public CommonResult<List<LeadAppealRespVO>> getList(@PathVariable Long leadId) {
        return success(appealService.getLeadAppeals(leadId, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/lead/{leadId}/submit")
    @Operation(summary = "提交客资申诉")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:appeal:create')")
    public CommonResult<Long> submit(@PathVariable Long leadId, @Valid @RequestBody LeadAppealSubmitReqVO reqVO) {
        return success(appealService.submit(leadId, WebFrameworkUtils.getLoginUserId(), reqVO));
    }

    @GetMapping("/inbox-page")
    @Operation(summary = "获得个人申诉待办或已办")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:appeal:query')")
    public CommonResult<PageResult<LeadAppealRespVO>> getInboxPage(@Valid LeadAppealPageReqVO reqVO) {
        return success(appealService.getInboxPage(reqVO, WebFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/inbox-cursor")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:appeal:query')")
    public CommonResult<CursorPageResult<LeadAppealRespVO>> getInboxCursor(@Valid LeadAppealPageReqVO reqVO) {
        return success(appealService.getInboxCursor(reqVO, WebFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/{id}/overturn")
    @Operation(summary = "改判客资有效")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:appeal:review-sales-manager','zsjos:lead:appeal:review-quality','zsjos:lead:appeal:review-chairman')")
    public CommonResult<Boolean> overturn(@PathVariable Long id, @Valid @RequestBody LeadAppealDecisionReqVO reqVO) {
        appealService.overturn(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PutMapping("/{id}/uphold")
    @Operation(summary = "维持客资无效")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:appeal:review-sales-manager','zsjos:lead:appeal:review-quality','zsjos:lead:appeal:review-chairman')")
    public CommonResult<Boolean> uphold(@PathVariable Long id, @Valid @RequestBody LeadAppealDecisionReqVO reqVO) {
        appealService.uphold(id, WebFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PostMapping("/attachment/upload")
    @Operation(summary = "上传申诉图片")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:appeal:create','zsjos:lead:appeal:review-sales-manager','zsjos:lead:appeal:review-quality','zsjos:lead:appeal:review-chairman')")
    public CommonResult<LeadAttachmentUploadRespVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return success(appealService.upload(file));
    }
}
