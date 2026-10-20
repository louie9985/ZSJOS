package cn.iocoder.yudao.module.zsjos.controller.admin.educationteam;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo.*;
import cn.iocoder.yudao.module.zsjos.service.educationteam.EducationTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 教务团队")
@RestController
@RequestMapping("/zsjos/education-team")
public class EducationTeamController {
    @Resource private cn.iocoder.yudao.module.zsjos.service.educationteam.EducationTeamScope navigationScope;
    /** Authenticated classification only; this does not grant page or operation access. */
    @GetMapping("/navigation-context")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<java.util.Map<String, Boolean>> navigationContext() {
        return success(java.util.Map.of("delivery",navigationScope.deliveryNavigation(getLoginUserId())));
    }
    @Resource private EducationTeamService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.educationteam.EducationTeamLeadReader reader;
    @GetMapping("/leads/{leadId}")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<LeadManagementRespVO> lead(@PathVariable Long leadId){return success(reader.get(leadId,getLoginUserId()));}

    @Resource private cn.iocoder.yudao.module.zsjos.service.educationteam.EducationTeamLeadOverturnService overturnService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.lead.LeadAttachmentService attachmentService;

    @PostMapping("/leads/{leadId}/overturn-valid")
    @Operation(summary = "主管直接改判客资有效")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:lead-overturn-valid')")
    public CommonResult<Boolean> overturnValid(@PathVariable Long leadId, @Valid @RequestBody EducationTeamOverturnValidReqVO request) {
        overturnService.overturn(leadId, getLoginUserId(), request);
        return success(true);
    }

    @PostMapping("/overturn-attachment/upload")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:lead-overturn-valid')")
    public CommonResult<cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO> uploadOverturnAttachment(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return success(attachmentService.upload(file));
    }

    @GetMapping("/page")
    @Operation(summary = "获得教务团队分页")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<PageResult<EducationTeamRespVO>> getPage(@Valid EducationTeamPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }

    @PostMapping("/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @Operation(summary = "筛选教务团队分页")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<PageResult<EducationTeamRespVO>> searchPage(
            @Valid @RequestBody EducationTeamPageReqVO reqVO) {
        return success(service.getPage(reqVO, getLoginUserId()));
    }

    @GetMapping("/{salesUserId}/overview")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<EducationTeamRespVO> getOverview(@PathVariable Long salesUserId) {
        return success(service.getOverview(salesUserId, getLoginUserId()));
    }

    @GetMapping("/{salesUserId}/leads")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<PageResult<LeadManagementRespVO>> getLeads(@PathVariable Long salesUserId,
                                                                    @Valid LeadManagementPageReqVO reqVO) {
        return success(service.getLeadPage(salesUserId, reqVO, getLoginUserId()));
    }

    @PostMapping("/{salesUserId}/leads/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<PageResult<LeadManagementRespVO>> searchLeads(@PathVariable Long salesUserId,
            @Valid @RequestBody LeadManagementPageReqVO reqVO) {
        return success(service.getLeadPage(salesUserId, reqVO, getLoginUserId()));
    }

    @GetMapping("/{salesUserId}/tasks")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<PageResult<EducationTeamTaskRespVO>> getTasks(@PathVariable Long salesUserId,
                                                                    @Valid EducationTeamTaskPageReqVO reqVO) {
        return success(service.getTaskPage(salesUserId, reqVO, getLoginUserId()));
    }

    @GetMapping("/transfer-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getTransferCandidates(@RequestParam(required = false) Long leadId) {
        return success(service.getTransferCandidates(getLoginUserId(), leadId));
    }

    @GetMapping("/batch-transfer-candidates")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:query')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getBatchTransferCandidates(
            @RequestParam List<Long> leadIds) {
        return success(service.getBatchTransferCandidates(getLoginUserId(), leadIds));
    }

    @PutMapping("/{salesUserId}/account-status")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:account-status')")
    public CommonResult<Boolean> updateAccountStatus(@PathVariable Long salesUserId,
                                                      @Valid @RequestBody EducationTeamAccountStatusReqVO reqVO) {
        service.updateAccountStatus(salesUserId, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/leads/{leadId}/transfer")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:lead-transfer')")
    public CommonResult<Boolean> transferLead(@PathVariable Long leadId,
                                               @Valid @RequestBody EducationTeamLeadActionReqVO reqVO) {
        service.transferLead(leadId, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/leads/{leadId}/restore")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:lead-restore')")
    public CommonResult<Boolean> restoreLead(@PathVariable Long leadId,
                                              @Valid @RequestBody EducationTeamLeadActionReqVO reqVO) {
        service.restoreLead(leadId, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/leads/{leadId}/recycle")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:lead-recycle')")
    public CommonResult<Boolean> recycleLead(@PathVariable Long leadId,
                                              @Valid @RequestBody EducationTeamLeadActionReqVO reqVO) {
        service.recycleLead(leadId, reqVO, getLoginUserId()); return success(true);
    }

    @PostMapping("/leads/batch-{action:transfer|restore|recycle}")
    @PreAuthorize("@ss.hasPermission('zsjos:education-team:lead-' + #action)")
    public CommonResult<EducationTeamBatchResultVO> batchLeadAction(@PathVariable String action,
            @Valid @RequestBody EducationTeamBatchLeadActionReqVO reqVO) {
        return success(service.batchLeadAction(action, reqVO, getLoginUserId()));
    }
}
