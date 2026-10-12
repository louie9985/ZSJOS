package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealReviewDetailRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
public class LeadAppealReviewDetailService {
    @Resource private LeadAppealService appealService;
    @Resource private LeadManagementService managementService;
    @Resource private LeadObjectPermissionService permissionService;
    @Resource private LeadMapper leadMapper;
    @Resource private SecurityFrameworkService security;

    @ZsjosPermission(bizType = "lead-appeal-review", bizId = "#appealId", action = "read")
    @Transactional(readOnly = true)
    public LeadAppealReviewDetailRespVO getDetail(Long appealId, String taskId, Long userId) {
        var context = appealService.getReviewContext(appealId, taskId, userId);
        var selected = context.getRecords().stream().filter(row -> row.getId().equals(appealId)).findFirst().orElseThrow();
        var lead = leadMapper.selectById(selected.getLeadId());
        LeadManagementRespVO projection;
        if (canReadFullDetail() && permissionService.canReadDetail(lead, userId)) {
            projection = managementService.getLead(lead.getId(), userId);
        } else {
            // A task-only reviewer gets the same authorized identity as the inbox, without contacts or other tabs.
            projection = new LeadManagementRespVO();
            projection.setId(lead.getId());
            projection.setLeadNo(selected.getLeadNo());
            projection.setSubmittedName(selected.getLeadName());
            projection.setStatus(lead.getStatus());
            projection.setQualificationStatus(LeadStateProjection.qualification(lead));
            projection.setVisibleTabs(List.of(DETAIL_TAB_APPEALS));
            projection.setAvailableActions(List.of());
            context.getRecords().stream().filter(row -> Boolean.TRUE.equals(row.getCurrentCycle()))
                    .reduce((previous, latest) -> latest).ifPresent(row -> {
                        var summary = new LeadManagementRespVO.AppealSummaryVO();
                        summary.setId(row.getId()); summary.setRoundNo(row.getRoundNo());
                        summary.setStatus(row.getStatus()); summary.setReviewStage(row.getReviewStage());
                        summary.setSubmittedAt(row.getSubmittedAt()); summary.setCanHandle(row.getTaskId() != null);
                        projection.setAppealSummary(summary);
                    });
        }
        var result = new LeadAppealReviewDetailRespVO();
        result.setLead(projection);
        result.setContext(context);
        return result;
    }

    private boolean canReadFullDetail() {
        // Match the ordinary detail Controller's feature gate as well as its object scope.
        return security.hasAnyPermissions("zsjos:lead:query", "zsjos:subordinate-sales:query",
                "zsjos:partner:query", "zsjos:partner:manage", "zsjos:partner:manage-all",
                "zsjos:student:query-my", "zsjos:media-student:query-my", "zsjos:sales-order:query",
                "zsjos:sales-order:review", "zsjos:lead-detail:follow-up-read", "zsjos:lead-detail:appeal-read",
                "zsjos:lead-detail:complaint-read", "zsjos:lead-detail:order-read", "zsjos:lead-detail:flow-read");
    }
}
