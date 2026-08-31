package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAttachmentService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadQualificationService;
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

@Tag(name = "管理后台 - 客资有效性判定")
@RestController
@RequestMapping("/zsjos/lead")
public class LeadQualificationController {
    @Resource private LeadQualificationService qualificationService;
    @Resource private LeadAttachmentService attachmentService;

    @PostMapping("/qualification/attachment/upload")
    @Operation(summary = "上传客资无效判定附件")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualify')")
    public CommonResult<LeadAttachmentUploadRespVO> uploadQualificationAttachment(
            @RequestParam("file") MultipartFile file) throws IOException {
        return success(attachmentService.upload(file));
    }

    @PostMapping("/{id}/judge-valid")
    @Operation(summary = "判定客资有效")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualify')")
    public CommonResult<Boolean> judgeValid(@PathVariable("id") Long id,
                                             @Valid @RequestBody LeadJudgeValidReqVO reqVO) {
        qualificationService.judgeValid(id, getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/{id}/judge-invalid")
    @Operation(summary = "判定客资无效")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualify')")
    public CommonResult<Boolean> judgeInvalid(@PathVariable("id") Long id,
                                               @Valid @RequestBody LeadJudgeInvalidReqVO reqVO) {
        qualificationService.judgeInvalid(id, getLoginUserId(), reqVO);
        return success(true);
    }

    @GetMapping("/qualification-exception/page")
    @Operation(summary = "获得挂起或回收待处理客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualification:query')")
    public CommonResult<PageResult<LeadQualificationExceptionRespVO>> getExceptionPage(
            @Valid LeadQualificationExceptionPageReqVO reqVO) {
        return success(qualificationService.getExceptionPage(reqVO, getLoginUserId()));
    }
    @PostMapping("/qualification-exception/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualification:query')")
    public CommonResult<PageResult<LeadQualificationExceptionRespVO>> searchExceptionPage(
            @Valid @RequestBody LeadQualificationExceptionPageReqVO reqVO) {
        return success(qualificationService.getExceptionPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/{id}/transfer-candidates")
    @Operation(summary = "获得主管可转派销售")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualification:manage')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getTransferCandidates(@PathVariable("id") Long id) {
        return success(qualificationService.getTransferCandidates(id, getLoginUserId()));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复挂起客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualification:manage')")
    public CommonResult<Boolean> restore(@PathVariable("id") Long id,
                                          @Valid @RequestBody LeadDispositionReqVO reqVO) {
        qualificationService.restore(id, getLoginUserId(), reqVO);
        return success(true);
    }

    // Keep the literal /batch prefix reserved for the batch action controller.
    @PostMapping("/{id:\\d+}/transfer")
    @Operation(summary = "转派挂起或回收待处理客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualification:manage')")
    public CommonResult<Boolean> transfer(@PathVariable("id") Long id,
                                           @Valid @RequestBody LeadTransferReqVO reqVO) {
        qualificationService.transfer(id, getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/{id}/recycle")
    @Operation(summary = "回收挂起客资")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualification:manage')")
    public CommonResult<Boolean> recycle(@PathVariable("id") Long id,
                                          @Valid @RequestBody LeadDispositionReqVO reqVO) {
        qualificationService.recycle(id, getLoginUserId(), reqVO);
        return success(true);
    }

    @PostMapping("/{id}/release-to-claim-pool")
    @Operation(summary = "释放挂起或回收待处理客资到抢单池")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:qualification:manage')")
    public CommonResult<Boolean> releaseToClaimPool(@PathVariable("id") Long id,
                                                     @Valid @RequestBody LeadDispositionReqVO reqVO) {
        qualificationService.releaseToClaimPool(id, getLoginUserId(), reqVO);
        return success(true);
    }
}
