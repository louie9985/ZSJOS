package cn.iocoder.yudao.module.zsjos.controller.admin.forcedform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo.*;
import cn.iocoder.yudao.module.zsjos.service.forcedform.ForcedFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 强制表单")
@RestController
@RequestMapping("/zsjos/forced-form")
@Validated
public class ForcedFormController {

    @Resource private ForcedFormService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:query')")
    public CommonResult<cn.iocoder.yudao.framework.common.pojo.PageResult<ForcedFormRespVO>> page(@Valid ForcedFormPageReqVO req) {
        return success(service.page(req));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:create')")
    public CommonResult<Long> create(@Valid @RequestBody ForcedFormSaveReqVO req) {
        return success(service.create(req, getLoginUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:update')")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody ForcedFormSaveReqVO req) {
        req.setId(id);
        service.update(req);
        return success(true);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:query')")
    public CommonResult<ForcedFormRespVO> get(@PathVariable Long id) {
        return success(service.get(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:delete')")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        service.delete(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:create')")
    public CommonResult<ForcedFormRespVO> copy(@PathVariable Long id) {
        return success(service.copy(id, getLoginUserId()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:publish')")
    public CommonResult<Boolean> publish(@PathVariable Long id) {
        service.publish(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:withdraw')")
    public CommonResult<Boolean> withdraw(@PathVariable Long id) {
        service.withdraw(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/{id}/recipient-preview")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:send')")
    public CommonResult<ForcedFormRecipientPreviewRespVO> recipientPreview(@PathVariable Long id,
                                                                           @Valid @RequestBody ForcedFormSendReqVO req) {
        return success(service.recipientPreview(id, req));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:send')")
    public CommonResult<ForcedFormSendRespVO> send(@PathVariable Long id,
                                                   @Valid @RequestBody ForcedFormSendReqVO req) {
        return success(service.send(id, req, getLoginUserId()));
    }

    @GetMapping("/pending")
    public CommonResult<java.util.List<ForcedFormPendingRespVO>> pending() {
        return success(service.pending(getLoginUserId()));
    }

    @GetMapping("/{id}/runtime")
    public CommonResult<ForcedFormRuntimeRespVO> runtime(@PathVariable Long id) {
        return success(service.runtime(id, getLoginUserId()));
    }

    @PostMapping("/{id}/attachment/upload")
    public CommonResult<ForcedFormAttachmentUploadRespVO> uploadAttachment(@PathVariable Long id,
                                                                           @RequestParam String fieldKey,
                                                                           @RequestPart("file") MultipartFile file) {
        return success(service.uploadAttachment(id, getLoginUserId(), fieldKey, file));
    }

    @PostMapping("/{id}/submit")
    public CommonResult<Boolean> submit(@PathVariable Long id, @Valid @RequestBody ForcedFormSubmitReqVO req) {
        service.submit(id, req, getLoginUserId());
        return success(true);
    }

    @GetMapping("/status")
    public CommonResult<ForcedFormStatusRespVO> status() {
        return success(service.status(getLoginUserId()));
    }

    @GetMapping("/submission/page")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:submission-query')")
    public CommonResult<cn.iocoder.yudao.framework.common.pojo.PageResult<ForcedFormSubmissionListRespVO>> submissionPage(
            @Valid ForcedFormSubmissionPageReqVO req) {
        return success(service.submissionPage(req));
    }

    @GetMapping("/submission/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:submission-read')")
    public CommonResult<ForcedFormSubmissionRespVO> submission(@PathVariable Long id) {
        return success(service.submission(id));
    }

    @PostMapping("/submission/export")
    @PreAuthorize("@ss.hasPermission('zsjos:forced-form:submission-export')")
    @Operation(summary = "导出提交记录")
    public void export(@Valid @RequestBody ForcedFormSubmissionPageReqVO req, HttpServletResponse response) {
        service.exportSubmissions(req, response);
    }
}
