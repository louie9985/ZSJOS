package cn.iocoder.yudao.module.zsjos.controller.admin.positioning;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardDraftRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningLinkRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardImportReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardImportRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardImportSourceRespVO;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningConfirmationService;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import java.util.List;

@Tag(name = "管理后台 - 新媒体定位卡")
@RestController
@RequestMapping("/zsjos/positioning-card")
@Validated
public class PositioningCardController {
    @Resource private PositioningCardService service;
    @Resource private PositioningConfirmationService confirmationService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.positioning.PositioningAssignmentService assignmentService;
    @GetMapping("/service-overview") @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningServiceOverviewRespVO> serviceOverview(@RequestParam Long serviceRelationId) {
        return success(service.serviceOverview(serviceRelationId, getLoginUserId()));
    }
    @PostMapping("/select-master") @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create')")
    public CommonResult<Boolean> selectMaster(@RequestParam Long serviceRelationId, @RequestParam Long cardId) {
        assignmentService.selectMaster(serviceRelationId, cardId, getLoginUserId()); return success(true);
    }
    @GetMapping("/application-options") @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningApplicationRespVO> applicationOptions(@RequestParam Long accountId) {
        return success(service.applicationOptions(accountId, getLoginUserId()));
    }
    @PostMapping("/apply") @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:apply')")
    public CommonResult<Boolean> apply(@Valid @RequestBody cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningApplyReqVO req) {
        assignmentService.apply(req, getLoginUserId()); return success(true);
    }
    @GetMapping("/account-overview")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningAccountOverviewRespVO> accountOverview(
            @RequestParam Long accountId) {
        return success(service.accountOverview(accountId, getLoginUserId()));
    }
    @PostMapping("/{id}/attachments")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create')")
    public CommonResult<PositioningCardService.CardFile> uploadAttachment(@PathVariable Long id,
            @RequestParam String fieldKey, @RequestParam org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return success(service.uploadAttachment(id, fieldKey, file.getBytes(), file.getOriginalFilename(), file.getContentType(), getLoginUserId()));
    }
    @GetMapping("/{id}/attachments/{fileId}")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<PositioningCardService.CardFile> attachment(@PathVariable Long id, @PathVariable Long fileId,
            @RequestParam(required = false) Long submissionId,
            @RequestParam(defaultValue = "false") boolean snapshot) {
        if (snapshot || submissionId != null) return success(resources.attachment(id, submissionId, fileId));
        return success(service.attachment(id, fileId, getLoginUserId()));
    }
    @Resource private cn.iocoder.yudao.module.zsjos.service.positioning.PositioningSnapshotResourceService resources;
    @Resource private cn.iocoder.yudao.module.zsjos.service.positioning.PositioningEvidenceService evidence;

    public record EvidenceRequest(@jakarta.validation.constraints.NotNull Integer version,
            @jakarta.validation.constraints.NotEmpty @Size(max=20) List<@jakarta.validation.constraints.NotNull Long> fileIds) {}

    @GetMapping("/{id}/snapshot/materials/{versionId}")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<java.util.Map<String, Object>> material(@PathVariable Long id, @PathVariable Long versionId,
            @RequestParam(required=false) Long submissionId) { return success(resources.material(id, submissionId, versionId)); }

    @GetMapping("/{id}/snapshot/attachments/{fileId}")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<PositioningCardService.CardFile> snapshotAttachment(@PathVariable Long id, @PathVariable Long fileId,
            @RequestParam(required=false) Long submissionId) { return success(resources.attachment(id, submissionId, fileId)); }

    @PostMapping("/{id}/submissions/{submissionId}/evidence/files")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:evidence')")
    public CommonResult<PositioningCardService.CardFile> uploadEvidence(@PathVariable Long id, @PathVariable Long submissionId,
            @RequestParam org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return success(evidence.upload(id, submissionId, file.getBytes(), file.getOriginalFilename(), file.getContentType(), getLoginUserId()));
    }

    @PostMapping("/{id}/submissions/{submissionId}/evidence")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:evidence')")
    public CommonResult<Boolean> submitEvidence(@PathVariable Long id, @PathVariable Long submissionId, @Valid @RequestBody EvidenceRequest request) {
        evidence.submit(id, submissionId, request.version(), request.fileIds(), getLoginUserId()); return success(true);
    }

    @GetMapping("/{id}/submissions/{submissionId}/evidence/{fileId}")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<PositioningCardService.CardFile> evidenceFile(@PathVariable Long id, @PathVariable Long submissionId, @PathVariable Long fileId) {
        return success(evidence.attachment(id, submissionId, fileId));
    }

    @GetMapping("/published-template") @Operation(summary = "获得当前定位卡业务模板")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create')")
    public CommonResult<DirectorFormTemplateVO.Snapshot> publishedTemplate(@RequestParam(required = false) Long templateId) {
        return success(service.getPublishedTemplate(templateId));
    }
    @PostMapping("/create") @Operation(summary = "创建定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create')")
    public CommonResult<PositioningCardDraftRespVO> create(@Valid @RequestBody PositioningCardSaveReqVO req) {
        return success(service.create(req, getLoginUserId()));
    }
    @PostMapping("/draft") @Operation(summary = "创建定位卡草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create')")
    public CommonResult<PositioningCardDraftRespVO> createDraft(@Valid @RequestBody PositioningCardSaveReqVO req) {
        return success(service.create(req, getLoginUserId()));
    }
    @GetMapping("/import-sources") @Operation(summary = "获得可导入的已提交定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create') && @ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<List<PositioningCardImportSourceRespVO>> importSources(
            @RequestParam Long studentPersonId, @RequestParam(required=false) Long accountId,
            @RequestParam Long serviceRelationId) {
        return success(service.getImportSources(studentPersonId, accountId, serviceRelationId, getLoginUserId()));
    }
    @PostMapping("/import") @Operation(summary = "导入已提交定位卡到目标草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create') && @ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<PositioningCardImportRespVO> importSubmission(
            @Valid @RequestBody PositioningCardImportReqVO req) {
        return success(service.importSubmission(req, getLoginUserId()));
    }
    @PutMapping("/draft/{id}") @Operation(summary = "保存定位卡草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:create')")
    public CommonResult<PositioningCardDraftRespVO> updateDraft(@PathVariable Long id,
                                                                 @Valid @RequestBody PositioningCardSaveReqVO req) {
        return success(service.updateDraft(id, req, getLoginUserId()));
    }
    @PostMapping("/{id}/submit") @Operation(summary = "提交定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:submit-review')")
    public CommonResult<Boolean> submit(@PathVariable Long id, @RequestParam Integer version) {
        service.submitReview(id, version, getLoginUserId()); return success(true);
    }
    @GetMapping("/get") @Operation(summary = "获得定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')")
    public CommonResult<PositioningCardRespVO> get(@RequestParam Long id) { return success(service.get(id, getLoginUserId())); }
    @GetMapping("/page") @Operation(summary="分页查询定位卡") @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:query')") public CommonResult<PageResult<PositioningCardRespVO>> page(@Valid PositioningCardPageReqVO req){ return success(service.page(req, getLoginUserId())); }
    @PostMapping("/{id}/submit-review") @Operation(summary = "提交定位审核")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:submit-review')")
    public CommonResult<Boolean> submitReview(@PathVariable Long id, @RequestParam Integer version) {
        service.submitReview(id, version, getLoginUserId()); return success(true);
    }
    @PostMapping("/{id}/operator-approve") @Operation(summary = "运营可行性复核通过")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:operator-confirm')")
    public CommonResult<Boolean> operatorApprove(@PathVariable Long id, @RequestParam Integer version) {
        service.operatorApprove(id, version); return success(true);
    }
    @PostMapping("/{id}/operator-reject") @Operation(summary = "运营可行性复核退回")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:operator-reject')")
    public CommonResult<Boolean> operatorReject(@PathVariable Long id, @RequestParam Integer version,
            @RequestParam @NotBlank @Size(max = 500) String reason) {
        service.operatorReject(id, version, reason); return success(true);
    }
    @PostMapping("/{id}/student-link") @Operation(summary = "生成学员定位卡确认链接")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:student-link-generate')")
    public CommonResult<PositioningLinkRespVO> generateStudentLink(@PathVariable Long id,
                                                                   @RequestParam Integer version) {
        return success(confirmationService.generateLink(id, version, getLoginUserId()));
    }
    @PostMapping("/{id}/start-revision") @Operation(summary = "开始修改已确认定位卡")
    @PreAuthorize("@ss.hasPermission('zsjos:positioning-card:edit')")
    public CommonResult<PositioningCardDraftRespVO> startRevision(@PathVariable Long id,
                                                                   @RequestParam Integer version) {
        return success(service.startRevision(id, version, getLoginUserId()));
    }
}
