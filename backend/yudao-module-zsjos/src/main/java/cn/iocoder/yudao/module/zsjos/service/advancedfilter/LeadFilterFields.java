package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class LeadFilterFields {
    private LeadFilterFields() {}

    static void register(Map<String, Field> result) {
        String intendedProduct = "SELECT 1 FROM zsjos_lead_intended_product lip WHERE lip.lead_id=l.id AND lip.tenant_id=l.tenant_id AND lip.deleted=b'0'";
        add(result, selectSource(Sensitivity.STANDARD, "lead.intendedProductRef", PRODUCT, "原始意向产品", "product-catalog:spu", bind("lead", "lip.product_ref", intendedProduct)));
        add(result, selectSource(Sensitivity.STANDARD, "lead.intendedSkuRef", PRODUCT, "原始意向 SKU", "product-catalog:sku", bind("lead", "lip.sku_ref", intendedProduct)));

        add(result, text(Sensitivity.STANDARD, "lead.leadNo", IDENTITY, "客资编号", bind("lead", "l.lead_no", null, "order", "rl.lead_no", leadFromOrder, "lead_appeal", "rl.lead_no", leadFromAppeal, "registration", "rl.lead_no", leadFromRegistration, "student", "rl.lead_no", leadFromStudent, "duplicate_review", "ml.lead_no", "SELECT 1 FROM zsjos_lead ml WHERE ml.id=dr.matched_lead_id AND ml.tenant_id=dr.tenant_id AND ml.deleted=b'0'")));
        add(result, text(Sensitivity.PERSONAL, "lead.name", IDENTITY, "提交姓名", leadBind("submitted_name", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "lead.mobile", IDENTITY, "提交手机号", leadBind("submitted_mobile", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text(Sensitivity.PERSONAL, "lead.wechatId", IDENTITY, "提交微信号", leadBind("submitted_wechat_id", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "lead.status", STATUS, "客资状态", "dict:zsjos_lead_status", leadBind("status", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "lead.assignmentStatus", STATUS, "分配状态", "dict:zsjos_lead_assignment_status", leadBind("assignment_status", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, select(Sensitivity.STANDARD, "lead.sourceType", EXTRA, "客资来源", options(LeadConstants.SOURCE_INTERNAL_NEW_MEDIA, "新媒体提交", LeadConstants.SOURCE_PARTNER, "兼职提交", LeadConstants.SOURCE_SALES_SELF, "销售自拓录", LeadConstants.SOURCE_EDUCATION_SELF, "教务自拓录"), leadBind("source_type", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, select(Sensitivity.STANDARD, "lead.ownerIdentity", EXTRA, "负责人身份", options(LeadConstants.OWNER_SALES, "销售", LeadConstants.OWNER_EDUCATION, "教务"), leadBind("owner_identity", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "lead.sourceChannel", EXTRA, "来源渠道", "dict:zsjos_lead_source_channel", leadBind("source_channel_id", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "lead.category", EXTRA, "客资分类", "dict:zsjos_lead_category", leadBind("lead_category", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "lead.salesStage", STATUS, "当前销售阶段", "dict:zsjos_lead_sales_stage", bind("lead", "l.sales_stage", null)));
        add(result, new Field(Sensitivity.PERSONAL, "lead.ownerDeptId", PEOPLE, "负责人所属组织（含下级）", "select", List.of("in", "not_in"), "visible-departments", List.of(), bind("lead", "l.owner_user_id", null)));
        add(result, select(Sensitivity.STANDARD, "lead.qualificationStatus", STATUS, "有效性", options(LeadConstants.QUALIFICATION_PENDING, "待判定", LeadConstants.QUALIFICATION_VALID, "有效", LeadConstants.QUALIFICATION_INVALID, "无效"), bind("lead", "CASE WHEN l.status='invalid' THEN 'invalid' WHEN l.status IN ('valid','won') THEN 'valid' ELSE 'pending' END", null)));
        add(result, select(Sensitivity.STANDARD, "lead.dealStatus", STATUS, "成交状态", options("won", "已成交", "not_won", "未成交"), bind("lead", "CASE WHEN l.status='won' THEN 'won' ELSE 'not_won' END", null)));
        add(result, date(Sensitivity.STANDARD, "lead.qualifiedAt", TIME, "有效性判定时间", bind("lead", "l.qualified_at", null)));
        add(result, date(Sensitivity.STANDARD, "lead.convertedAt", TIME, "成交时间", bind("lead", "op.won_at", "SELECT 1 FROM zsjos_opportunity op WHERE op.lead_id=l.id AND op.type='initial_conversion' AND op.tenant_id=l.tenant_id AND op.deleted=b'0'")));
        add(result, selectSource(Sensitivity.PERSONAL, "lead.sourceUserId", PEOPLE, "提交人", "visible-users",
                leadSubmitterFilterBind(leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource(Sensitivity.PERSONAL, "lead.ownerUserId", PEOPLE, "负责人", "visible-users", leadBind("owner_user_id", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.submittedAt", TIME, "客资提交时间", leadBind("submitted_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.lastFollowUpAt", TIME, "最近跟进时间", leadBind("last_follow_up_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.nextFollowUpAt", TIME, "下次跟进时间", leadBind("next_follow_up_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.ownershipStartedAt", TIME, "持有起点", leadBind("ownership_started_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "lead.remark", EXTRA, "客资备注", leadBind("remark", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.lastActivityAt", TIME, "最近活动时间", leadBind("last_activity_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.currentAssignmentFirstFollowUpAt", TIME, "本次分配首次跟进时间", leadBind("current_assignment_first_follow_up_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.currentAssignmentFirstFollowUpDeadlineAt", TIME, "本次分配首跟截止时间", leadBind("current_assignment_first_follow_up_deadline_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.qualificationStartedAt", TIME, "当前有效性判定开始时间", leadBind("qualification_started_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.qualificationDeadlineAt", TIME, "当前有效性判定截止时间", leadBind("qualification_deadline_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.suspendedAt", TIME, "挂起时间", leadBind("suspended_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.appealDeadlineAt", TIME, "申诉截止时间", leadBind("appeal_deadline_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.closedAt", TIME, "关闭时间", leadBind("closed_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, date(Sensitivity.STANDARD, "lead.publicPoolAt", TIME, "进入抢单池时间", leadBind("public_pool_at", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "lead.validDescription", EXTRA, "有效判定说明", leadBind("valid_description", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "lead.invalidDescription", EXTRA, "无效判定说明", leadBind("invalid_description", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, text(Sensitivity.FREE_TEXT, "lead.closeReason", EXTRA, "关闭原因", leadBind("close_reason", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
        add(result, selectSource(Sensitivity.STANDARD, "lead.invalidReason", EXTRA, "无效原因", "dict:zsjos_lead_invalid_reason", leadBind("invalid_reason", leadFromOrder, leadFromAppeal, leadFromRegistration, leadFromStudent)));
    }
}
