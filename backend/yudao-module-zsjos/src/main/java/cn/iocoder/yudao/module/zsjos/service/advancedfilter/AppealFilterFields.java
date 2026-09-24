package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class AppealFilterFields {
    private AppealFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, number(Sensitivity.STANDARD, "appeal.roundNo", STATUS, "申诉轮次", bind("lead_appeal", "a.round_no", null)));
        add(result, select(Sensitivity.STANDARD, "appeal.reviewStage", STATUS, "审核阶段", options(LeadConstants.APPEAL_STAGE_SALES_MANAGER, "销售主管", LeadConstants.APPEAL_STAGE_QUALITY, "质控部门", LeadConstants.APPEAL_STAGE_CHAIRMAN, "董事长"), bind("lead_appeal", "a.review_stage", null)));
        add(result, selectSource(Sensitivity.STANDARD, "appeal.status", STATUS, "申诉状态", "dict:zsjos_lead_appeal_status", bind("lead_appeal", "a.status", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "appeal.applicantUserId", PEOPLE, "申请人", "visible-users", bind("lead_appeal", "a.applicant_user_id", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "appeal.reviewerUserId", PEOPLE, "审核人", "visible-users", bind("lead_appeal", "a.reviewer_user_id", null)));
        add(result, text(Sensitivity.FREE_TEXT, "appeal.reason", EXTRA, "申诉原因", bind("lead_appeal", "a.reason", null)));
        add(result, text(Sensitivity.FREE_TEXT, "appeal.decisionReason", EXTRA, "裁决意见", bind("lead_appeal", "a.decision_reason", null)));
        add(result, date(Sensitivity.STANDARD, "appeal.submittedAt", TIME, "申诉提交时间", bind("lead_appeal", "a.submitted_at", null)));
        add(result, date(Sensitivity.STANDARD, "appeal.decidedAt", TIME, "申诉处理时间", bind("lead_appeal", "a.decided_at", null)));
        add(result, select(Sensitivity.STANDARD, "appeal.hasEvidence", EXTRA, "申诉附件", options("present", "有附件", "absent", "无附件"), bind("lead_appeal", "CASE WHEN NULLIF(TRIM(a.evidence_refs),'') IS NULL THEN 'absent' ELSE 'present' END", null)));
        add(result, text(Sensitivity.FREE_TEXT, "appeal.invalidReasonSnapshot", EXTRA, "原判无效原因（历史标签）", bind("lead_appeal", "a.invalid_reason_snapshot", null)));
        add(result, text(Sensitivity.FREE_TEXT, "appeal.invalidDescriptionSnapshot", EXTRA, "原判无效说明", bind("lead_appeal", "a.invalid_description_snapshot", null)));
    }

}
