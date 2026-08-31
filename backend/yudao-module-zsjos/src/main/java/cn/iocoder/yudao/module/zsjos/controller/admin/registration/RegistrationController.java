package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.service.registration.RegistrationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.PERMISSION_CLOSE;

@Tag(name = "员工工作台 - 报名履约公共池")
@RestController
@RequestMapping("/zsjos/registration")
public class RegistrationController {
    @Resource private RegistrationService registrationService;

    @GetMapping("/pool-page")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:query-pool')")
    public CommonResult<PageResult<RegistrationCaseRespVO>> getPoolPage(@Valid RegistrationPoolPageReqVO reqVO) {
        return success(registrationService.getPoolPage(reqVO));
    }

    @PostMapping("/pool/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:registration:query-pool')")
    public CommonResult<PageResult<RegistrationCaseRespVO>> searchPoolPage(
            @Valid @RequestBody RegistrationPoolPageReqVO reqVO) {
        return success(registrationService.getPoolPage(reqVO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:query-pool')")
    public CommonResult<RegistrationCaseRespVO> get(@PathVariable Long id) { return success(registrationService.getCase(id)); }

    @GetMapping("/study-planner-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<List<StudyPlannerSimpleRespVO>> candidates() {
        return success(registrationService.getStudyPlannerCandidates(SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{id}/routes/{routeId}/candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<List<StudyPlannerSimpleRespVO>> routeCandidates(@PathVariable Long id, @PathVariable Long routeId) {
        return success(registrationService.getRouteCandidates(id, routeId, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/{id}/items/{itemId}")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<RegistrationCaseRespVO> updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                             @Valid @RequestBody RegistrationChecklistItemUpdateReqVO reqVO) {
        return success(registrationService.updateChecklistItem(id, itemId, SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PutMapping("/{id}/study-planner")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<RegistrationCaseRespVO> updatePlanner(@PathVariable Long id,
                                                @Valid @RequestBody RegistrationPlannerUpdateReqVO reqVO) {
        return success(registrationService.updateStudyPlanner(id, SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PutMapping("/{id}/routes")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<RegistrationCaseRespVO> updateRoutes(@PathVariable Long id,
                                                @Valid @RequestBody RegistrationRoutesUpdateReqVO reqVO) {
        return success(registrationService.updateRoutes(id, SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/{id}/items/{itemId}/attachments")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<RegistrationAttachmentUploadRespVO> uploadAttachment(
            @PathVariable Long id, @PathVariable Long itemId,
            @RequestParam Integer version, @NotBlankIdempotency @RequestParam String idempotencyKey,
            @RequestParam("file") MultipartFile file) throws IOException {
        return success(registrationService.uploadAttachment(id, itemId, SecurityFrameworkUtils.getLoginUserId(),
                version, idempotencyKey, file));
    }

    @DeleteMapping("/{id}/items/{itemId}/attachments/{attachmentId}")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:update')")
    public CommonResult<RegistrationCaseRespVO> deleteAttachment(
            @PathVariable Long id, @PathVariable Long itemId, @PathVariable Long attachmentId,
            @Valid @RequestBody RegistrationAttachmentDeleteReqVO reqVO) {
        return success(registrationService.deleteAttachment(id, itemId, attachmentId,
                SecurityFrameworkUtils.getLoginUserId(), reqVO));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@ss.hasPermission('zsjos:registration:complete')")
    public CommonResult<Boolean> complete(@PathVariable Long id, @Valid @RequestBody RegistrationVersionReqVO reqVO) {
        registrationService.complete(id, SecurityFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ss.hasPermission('" + PERMISSION_CLOSE + "')")
    public CommonResult<Boolean> close(@PathVariable Long id, @Valid @RequestBody RegistrationCloseReqVO reqVO) {
        registrationService.close(id, SecurityFrameworkUtils.getLoginUserId(), reqVO); return success(true);
    }
}
