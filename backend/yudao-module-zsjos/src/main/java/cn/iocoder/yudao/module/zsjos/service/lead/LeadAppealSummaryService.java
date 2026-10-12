package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO.AppealSummaryVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;

/** Minimal lifecycle metadata for already-authorized leads; appeal contents retain their own permissions. */
@Service
public class LeadAppealSummaryService {
    @Resource private LeadAppealMapper appealMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper eventMapper;
    @Resource private LeadAppealService appealService;

    public void populate(List<LeadManagementRespVO> rows, Long userId, boolean detail) {
        if (rows.isEmpty()) return;
        var summaries = new HashMap<Long, AppealSummaryVO>();
        var cycles = eventMapper.selectLatestInvalidIds(rows.stream().map(LeadManagementRespVO::getId).toList());
        for (var appeal : appealMapper.selectListByLeadIds(rows.stream().map(LeadManagementRespVO::getId).toList())) {
            if (!LeadAppealCycles.active(appeal) && LeadAppealCycles.key(appeal) != cycles.getOrDefault(appeal.getLeadId(), 0L)) continue;
            var current = summaries.get(appeal.getLeadId());
            if (current != null && current.getRoundNo() >= appeal.getRoundNo()) continue;
            var summary = new AppealSummaryVO();
            summary.setId(appeal.getId());
            summary.setRoundNo(appeal.getRoundNo());
            summary.setStatus(appeal.getStatus());
            summary.setReviewStage(appeal.getReviewStage());
            summary.setSubmittedAt(appeal.getSubmittedAt());
            summaries.put(appeal.getLeadId(), summary);
        }
        for (var row : rows) {
            var summary = summaries.get(row.getId());
            if (detail && summary != null) summary.setCanHandle(appealService.canHandleAppeal(summary.getId(), userId));
            row.setAppealSummary(summary);
        }
    }
}
