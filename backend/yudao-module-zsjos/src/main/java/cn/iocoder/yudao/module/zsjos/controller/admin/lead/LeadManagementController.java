package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBasicInfoUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterSupplementReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterAssistRequestReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadUrgeReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmitterActionService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadFlowHistoryService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadBatchActionService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchResultVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBatchActionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.flow.LeadFlowHistoryRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;
import java.util.List;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.INBOX_AUDIENCE_OWNER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.INBOX_AUDIENCE_SUBMITTER;

@Tag(name = "管理后台 - 客资管理")
@RestController
@RequestMapping("/zsjos/lead")
public class LeadManagementController {

    @Resource
    private LeadManagementService leadManagementService;
    @Resource private LeadSubmitterActionService submitterActionService;
    @Resource private LeadFlowHistoryService leadFlowHistoryService;
    @Resource private LeadBatchActionService leadBatchActionService;

    @GetMapping("/page")
    @Operation(summary = "获得客资分页")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query')")
    public CommonResult<PageResult<LeadManagementRespVO>> getLeadPage(
            @Valid LeadManagementPageReqVO reqVO) {
        return success(leadManagementService.getLeadPage(reqVO, getLoginUserId()));
    }

    @PostMapping("/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @Operation(summary = "高级筛选客资分页")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query')")
    public CommonResult<PageResult<LeadManagementRespVO>> searchLeadPage(@Valid @RequestBody LeadManagementPageReqVO reqVO) {
        return success(leadManagementService.getLeadPage(reqVO, getLoginUserId()));
    }
    @GetMapping("/cursor")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query')")
    public CommonResult<CursorPageResult<LeadManagementRespVO>> getLeadCursor(@Valid LeadManagementPageReqVO reqVO) {
        return success(leadManagementService.getLeadCursor(reqVO, getLoginUserId()));
    }
    @PostMapping("/search-cursor")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query')")
    public CommonResult<CursorPageResult<LeadManagementRespVO>> searchLeadCursor(@Valid @RequestBody LeadManagementPageReqVO reqVO) {
        return success(leadManagementService.getLeadCursor(reqVO, getLoginUserId()));
    }

    @PostMapping("/inbox/submitted/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-submitted')")
    public CommonResult<PageResult<LeadManagementRespVO>> searchSubmitted(@Valid @RequestBody LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_SUBMITTER); reqVO.setRelationScope("submitted");
        return success(leadManagementService.getLeadPage(reqVO, getLoginUserId()));
    }
    @PostMapping("/inbox/submitted/search-cursor")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-submitted')")
    public CommonResult<CursorPageResult<LeadManagementRespVO>> searchSubmittedCursor(@Valid @RequestBody LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_SUBMITTER); reqVO.setRelationScope("submitted");
        return success(leadManagementService.getLeadCursor(reqVO, getLoginUserId()));
    }

    @PostMapping("/inbox/owned/search-page")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-owned')")
    public CommonResult<PageResult<LeadManagementRespVO>> searchOwned(@Valid @RequestBody LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_OWNER); reqVO.setRelationScope("owned");
        return success(leadManagementService.getLeadPage(reqVO, getLoginUserId()));
    }
    @PostMapping("/inbox/owned/search-cursor")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-owned')")
    public CommonResult<CursorPageResult<LeadManagementRespVO>> searchOwnedCursor(@Valid @RequestBody LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_OWNER); reqVO.setRelationScope("owned");
        return success(leadManagementService.getLeadCursor(reqVO, getLoginUserId()));
    }

    @GetMapping("/inbox/submitted/page")
    @Operation(summary = "获得我提交的客资分页")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-submitted')")
    public CommonResult<PageResult<LeadManagementRespVO>> getSubmittedLeadPage(
            @Valid LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_SUBMITTER);
        reqVO.setRelationScope("submitted");
        return success(leadManagementService.getLeadPage(reqVO, getLoginUserId()));
    }
    @GetMapping("/inbox/submitted/cursor")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-submitted')")
    public CommonResult<CursorPageResult<LeadManagementRespVO>> getSubmittedLeadCursor(@Valid LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_SUBMITTER); reqVO.setRelationScope("submitted");
        return success(leadManagementService.getLeadCursor(reqVO, getLoginUserId()));
    }

    @GetMapping("/inbox/owned/page")
    @Operation(summary = "获得我负责的客资分页")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-owned')")
    public CommonResult<PageResult<LeadManagementRespVO>> getOwnedLeadPage(
            @Valid LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_OWNER);
        reqVO.setRelationScope("owned");
        return success(leadManagementService.getLeadPage(reqVO, getLoginUserId()));
    }
    @GetMapping("/inbox/owned/cursor")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-owned')")
    public CommonResult<CursorPageResult<LeadManagementRespVO>> getOwnedLeadCursor(@Valid LeadManagementPageReqVO reqVO) {
        reqVO.setAudience(INBOX_AUDIENCE_OWNER); reqVO.setRelationScope("owned");
        return success(leadManagementService.getLeadCursor(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得客资详情")
    @Parameter(name = "id", description = "内部客资ID", required = true)
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:subordinate-sales:query','zsjos:partner:query','zsjos:partner:manage','zsjos:student:query-my','zsjos:media-student:query-my','zsjos:sales-order:query','zsjos:sales-order:review','zsjos:lead-detail:follow-up-read','zsjos:lead-detail:appeal-read','zsjos:lead-detail:complaint-read','zsjos:lead-detail:order-read','zsjos:lead-detail:flow-read')")
    public CommonResult<LeadManagementRespVO> getLead(@RequestParam("id") Long id) {
        return success(leadManagementService.getLead(id, getLoginUserId()));
    }

    @GetMapping("/get-by-no")
    @Operation(summary = "按客资编号获得客资详情")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:query','zsjos:subordinate-sales:query','zsjos:partner:query','zsjos:partner:manage','zsjos:student:query-my','zsjos:media-student:query-my','zsjos:sales-order:query','zsjos:sales-order:review','zsjos:lead-detail:follow-up-read','zsjos:lead-detail:appeal-read','zsjos:lead-detail:complaint-read','zsjos:lead-detail:order-read','zsjos:lead-detail:flow-read')")
    public CommonResult<LeadManagementRespVO> getLeadByLeadNo(@RequestParam("leadNo") String leadNo) {
        return success(leadManagementService.getLeadByLeadNo(leadNo, getLoginUserId()));
    }

    @GetMapping("/{id}/flow-history")
    @Operation(summary = "获得客资流转记录")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead-detail:flow-read','zsjos:partner:query','zsjos:partner:manage')")
    public CommonResult<List<LeadFlowHistoryRespVO>> getFlowHistory(@PathVariable("id") Long id) {
        return success(leadFlowHistoryService.getHistory(id));
    }

    @PutMapping("/{id}/basic-info")
    @Operation(summary = "修改客资基础信息")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:update')")
    public CommonResult<Boolean> updateBasicInfo(@PathVariable("id") Long id,
                                                  @Valid @RequestBody LeadBasicInfoUpdateReqVO reqVO) {
        leadManagementService.updateBasicInfo(id, getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/{id}/submitter-supplement")
    @Operation(summary = "提交人补充非身份资料")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:submitter-supplement')")
    public CommonResult<Boolean> supplement(@PathVariable("id") Long id,
                                             @Valid @RequestBody LeadSubmitterSupplementReqVO reqVO) {
        submitterActionService.supplement(id, getLoginUserId(), reqVO); return success(true);
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/urge")
    @Operation(summary = "提交人催促当前责任销售")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:urge')")
    public CommonResult<Boolean> urge(@PathVariable("id") Long id, @Valid @RequestBody LeadUrgeReqVO reqVO) {
        submitterActionService.urge(id, getLoginUserId(), reqVO); return success(true);
    }

    @PostMapping("/batch/{action}")
    @Operation(summary = "批量处置客资")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:lead:qualification:manage','zsjos:lead:owner-transfer','zsjos:lead:owner-release-public-sea','zsjos:subordinate-sales:lead-transfer','zsjos:subordinate-sales:lead-restore','zsjos:subordinate-sales:lead-recycle','zsjos:subordinate-sales:lead-release-claim-pool','zsjos:subordinate-sales:lead-release-public-sea')")
    public CommonResult<SubordinateBatchResultVO> batchAction(@PathVariable String action,
                                                               @Valid @RequestBody LeadBatchActionReqVO reqVO) {
        return success(leadBatchActionService.execute(action, reqVO, getLoginUserId()));
    }

    @PostMapping("/{id}/submitter-assist-request")
    @Operation(summary = "请求客资提交人协助")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:request-submitter-assist')")
    public CommonResult<Long> requestSubmitterAssist(@PathVariable("id") Long id,
            @Valid @RequestBody LeadSubmitterAssistRequestReqVO reqVO) {
        return success(submitterActionService.requestAssist(id, getLoginUserId(), reqVO));
    }

    @GetMapping("/status-counts")
    @Operation(summary = "获得客资状态统计")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query')")
    public CommonResult<Map<String, Long>> getStatusCounts() {
        return success(leadManagementService.getStatusCounts(getLoginUserId()));
    }

    @GetMapping("/visible-users")
    @Operation(summary = "获得当前客资范围内的用户")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query')")
    public CommonResult<List<LeadAssignmentUserRespVO>> getVisibleUsers() {
        return success(leadManagementService.getVisibleUsers(getLoginUserId()));
    }

    @GetMapping("/inbox/filter-profile")
    @Operation(summary = "获得客资收件箱筛选配置")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query')")
    public CommonResult<LeadInboxFilterProfileRespVO> getInboxFilterProfile(
            @RequestParam(value = "audience", defaultValue = "submitter") String audience) {
        return success(leadManagementService.getInboxFilterProfile(getLoginUserId(), audience));
    }

    @GetMapping("/inbox/submitted/filter-profile")
    @Operation(summary = "获得我提交的客资筛选配置")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-submitted')")
    public CommonResult<LeadInboxFilterProfileRespVO> getSubmittedInboxFilterProfile() {
        return success(leadManagementService.getInboxFilterProfile(getLoginUserId(), INBOX_AUDIENCE_SUBMITTER));
    }

    @GetMapping("/inbox/owned/filter-profile")
    @Operation(summary = "获得我负责的客资筛选配置")
    @PreAuthorize("@ss.hasPermission('zsjos:lead:query') && @ss.hasPermission('zsjos:lead:query-owned')")
    public CommonResult<LeadInboxFilterProfileRespVO> getOwnedInboxFilterProfile() {
        return success(leadManagementService.getInboxFilterProfile(getLoginUserId(), INBOX_AUDIENCE_OWNER));
    }
}
