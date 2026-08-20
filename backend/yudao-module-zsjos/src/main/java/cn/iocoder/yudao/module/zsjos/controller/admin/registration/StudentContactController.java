package cn.iocoder.yudao.module.zsjos.controller.admin.registration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/student/service")
@Validated
public class StudentContactController {

    @Resource
    private StudentContactService service;

    @GetMapping("/{relationId}/contact-context")
    @PreAuthorize("@ss.hasPermission('zsjos:student:query-my')")
    public CommonResult<StudentContactContextRespVO> getContext(@PathVariable Long relationId) {
        return success(service.getContext(relationId, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{relationId}/contact-records")
    @PreAuthorize("@ss.hasPermission('zsjos:student:query-my')")
    public CommonResult<List<StudentContactRecordRespVO>> getRecords(@PathVariable Long relationId) {
        return success(service.getRecords(relationId, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/{relationId}/accept")
    @PreAuthorize("@ss.hasPermission('zsjos:student:accept')")
    public CommonResult<Boolean> accept(@PathVariable Long relationId,
                                        @Valid @RequestBody StudentServiceAcceptReqVO request) {
        service.accept(relationId, request, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/{relationId}/first-contact")
    @PreAuthorize("@ss.hasPermission('zsjos:student-contact:first-submit')")
    public CommonResult<Long> submitFirstContact(@PathVariable Long relationId,
                                                  @Valid @RequestBody StudentFirstContactSubmitReqVO request) {
        return success(service.submitFirstContact(relationId, request, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/{relationId}/study-plan")
    @PreAuthorize("@ss.hasPermission('zsjos:student-contact:study-plan-submit')")
    public CommonResult<Long> submitStudyPlan(@PathVariable Long relationId,
                                               @Valid @RequestBody StudentStudyPlanSubmitReqVO request) {
        return success(service.submitStudyPlan(relationId, request, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/{relationId}/contacts")
    @PreAuthorize("@ss.hasPermission('zsjos:student-contact:submit')")
    public CommonResult<Long> submitContact(@PathVariable Long relationId,
                                             @Valid @RequestBody StudentContactSubmitReqVO request) {
        return success(service.submitContact(relationId, request, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{relationId}/collaborator-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:student-collaborator:assign')")
    public CommonResult<List<StudyPlannerSimpleRespVO>> getCollaboratorCandidates(
            @PathVariable Long relationId, @RequestParam String type) {
        return success(service.getCollaboratorCandidates(relationId, type, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/{relationId}/collaborators")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:student-collaborator:assign', 'zsjos:student-collaborator:correct')")
    public CommonResult<Boolean> assignCollaborator(@PathVariable Long relationId,
                                                     @Valid @RequestBody StudentCollaboratorAssignReqVO request) {
        service.assignCollaborator(relationId, request, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/extensions/{extensionId}/withdraw")
    @PreAuthorize("@ss.hasPermission('zsjos:student-contact-extension:apply')")
    public CommonResult<Boolean> withdrawExtension(@PathVariable Long extensionId,
                                                    @Valid @RequestBody ExtensionWithdrawReqVO request) {
        service.withdrawExtension(extensionId, request.getReason(), SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @GetMapping("/extensions")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:student-contact-extension:apply', 'zsjos:student-contact-extension:review')")
    public CommonResult<List<StudentContactExtensionRespVO>> getExtensions() {
        return success(service.getExtensions(SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/assistance/{taskId}/complete")
    @PreAuthorize("@ss.hasPermission('zsjos:student-contact-extension:review')")
    public CommonResult<Boolean> completeAssistance(@PathVariable Long taskId,
                                                     @Valid @RequestBody AssistanceCompleteReqVO request) {
        service.completeAssistance(taskId, request.getRemark(), SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PostMapping("/attachments")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:student-contact:first-submit', 'zsjos:student-contact:study-plan-submit', "
            + "'zsjos:student-contact:submit', 'zsjos:student-contact-extension:apply')")
    public CommonResult<StudentContactAttachmentRespVO> uploadAttachment(@RequestParam("file") MultipartFile file)
            throws IOException {
        return success(service.uploadAttachment(file));
    }

    @Data
    public static class ExtensionWithdrawReqVO {
        @NotBlank
        private String reason;
    }

    @Data
    public static class AssistanceCompleteReqVO {
        @NotBlank
        private String remark;
    }
}
