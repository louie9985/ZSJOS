package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class DuplicateReviewFilterFields {
    private DuplicateReviewFilterFields() {}

    static void register(Map<String, Field> result) {
        add(result, text(Sensitivity.PERSONAL, "review.submittedName", IDENTITY, "提交姓名", bind("duplicate_review", json("name"), null)));
        add(result, text(Sensitivity.PERSONAL, "review.submittedMobile", IDENTITY, "提交手机号", bind("duplicate_review", json("mobile"), null)));
        add(result, text(Sensitivity.PERSONAL, "review.submittedWechatId", IDENTITY, "提交微信号", bind("duplicate_review", json("wechatId"), null)));
        add(result, select(Sensitivity.STANDARD, "review.status", STATUS, "复核状态", options(LeadConstants.DUPLICATE_REVIEW_STATUS_PENDING, "待处理", LeadConstants.DUPLICATE_REVIEW_STATUS_COMPLETED, "已处理"), bind("duplicate_review", "dr.status", null)));
        add(result, select(Sensitivity.STANDARD, "review.resultType", STATUS, "复核结果", options(LeadConstants.DUPLICATE_REVIEW_ACTION_ALLOW_FLOW, "放行", LeadConstants.DUPLICATE_REVIEW_ACTION_CLOSE_DUPLICATE, "确认重复关闭", LeadConstants.DUPLICATE_RESULT_STRONG_REJECTED, "强重复拦截", LeadConstants.DUPLICATE_RESULT_AUTO_CLOSED, "自动关闭"), bind("duplicate_review", "dr.result_type", null)));
        add(result, select(Sensitivity.STANDARD, "review.duplicateFlag", STATUS, "重复类型", options(LeadConstants.DUPLICATE_FLAG_STRONG, "强重复", LeadConstants.DUPLICATE_FLAG_SUSPECTED, "疑似重复", LeadConstants.DUPLICATE_FLAG_NONE, "未重复"), bind("duplicate_review", "dr.duplicate_flag", null)));
        add(result, select(Sensitivity.STANDARD, "review.duplicateResult", STATUS, "查重结果", options(LeadConstants.DUPLICATE_RESULT_STRONG_REJECTED, "强重复拦截", LeadConstants.DUPLICATE_RESULT_SUSPECTED_CREATED, "疑似重复待确认", LeadConstants.DUPLICATE_RESULT_ALLOWED, "已放行", LeadConstants.DUPLICATE_RESULT_CLOSED, "已关闭", LeadConstants.DUPLICATE_RESULT_AUTO_CLOSED, "自动关闭"), bind("duplicate_review", "dr.duplicate_result", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "review.submitterUserId", PEOPLE, "复核提交人", "visible-users", bind("duplicate_review", "dr.submitter_user_id", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "review.reviewerUserId", PEOPLE, "复核人", "visible-users", bind("duplicate_review", "dr.reviewer_user_id", null)));
        add(result, selectSource(Sensitivity.PERSONAL, "review.selectedSalesUserId", PEOPLE, "选定销售", "visible-users", bind("duplicate_review", "dr.selected_sales_user_id", null)));
        add(result, text(Sensitivity.STANDARD, "review.submissionSource", EXTRA, "提交来源", bind("duplicate_review", "dr.submission_source_type", null)));
        add(result, text(Sensitivity.STANDARD, "review.matchRules", EXTRA, "匹配规则", bind("duplicate_review", "dr.match_rules", null)));
        add(result, text(Sensitivity.FREE_TEXT, "review.opinion", EXTRA, "复核意见", bind("duplicate_review", "dr.review_opinion", null)));
        add(result, date(Sensitivity.STANDARD, "review.submittedAt", TIME, "复核提交时间", bind("duplicate_review", "dr.create_time", null)));
        add(result, date(Sensitivity.STANDARD, "review.reviewedAt", TIME, "复核完成时间", bind("duplicate_review", "dr.reviewed_at", null)));
        add(result, select(Sensitivity.STANDARD, "review.hasAttachments", EXTRA, "复核附件", options("present", "有附件", "absent", "无附件"), bind("duplicate_review", "CASE WHEN NULLIF(TRIM(dr.review_attachments),'') IS NULL THEN 'absent' ELSE 'present' END", null)));
    }

}
