package cn.iocoder.yudao.module.zsjos.service.media;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MediaNotificationTenantInitializer {
    @Resource private NotifyRuleApi rules;
    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> rules.initializeDefaultRules(defaultRules()));
    }
    static List<NotifyDefaultRuleReqDTO> defaultRules() {
        return List.of(
            NotifyDefaultRuleReqDTO.builder().name("内容已待验收组批").sceneCode(MEDIA_CONTENT_PENDING_ACCEPTANCE)
                .templateCode("ZSJ_N269_MEDIA_CONTENT_PENDING_ACCEPTANCE").recipientRoles(List.of("assignee"))
                .actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("定位访谈已预约").sceneCode(MEDIA_STUDENT_INTERVIEW_SCHEDULED).templateCode("ZSJ_N269_MEDIA_STUDENT_INTERVIEW_SCHEDULED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("定位访谈时间提醒").sceneCode(MEDIA_STUDENT_INTERVIEW_REMINDER).templateCode("ZSJ_N269_MEDIA_STUDENT_INTERVIEW_REMINDER")
                .recipientRoles(List.of("assignee")).actionType("business_detail").timingStage("advance").timingOffsetMinutes(60).build(),
            NotifyDefaultRuleReqDTO.builder().name("定位访谈已完成").sceneCode(MEDIA_STUDENT_INTERVIEW_COMPLETED).templateCode("ZSJ_N269_MEDIA_STUDENT_INTERVIEW_COMPLETED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("学员协作责任已移交").sceneCode(MEDIA_STUDENT_COLLABORATOR_CHANGED).templateCode("ZSJ_N269_MEDIA_STUDENT_COLLABORATOR_CHANGED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("定位运营复核通过").sceneCode(MEDIA_POSITIONING_OPERATOR_APPROVED).templateCode("ZSJ_N269_MEDIA_POSITIONING_OPERATOR_APPROVED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("学员确认链接已生成").sceneCode(MEDIA_POSITIONING_CONFIRMATION_READY).templateCode("ZSJ_N269_MEDIA_POSITIONING_CONFIRMATION_READY")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("定位确认凭证已完成").sceneCode(MEDIA_POSITIONING_EFFECTIVE).templateCode("ZSJ_N269_MEDIA_POSITIONING_EFFECTIVE")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("账号应用定位已更新").sceneCode(MEDIA_POSITIONING_APPLIED).templateCode("ZSJ_N269_MEDIA_POSITIONING_APPLIED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("定位已开始修订").sceneCode(MEDIA_POSITIONING_REVISION_STARTED).templateCode("ZSJ_N269_MEDIA_POSITIONING_REVISION_STARTED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("媒体账号已创建").sceneCode(MEDIA_ACCOUNT_CREATED).templateCode("ZSJ_N269_MEDIA_ACCOUNT_CREATED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("账号诊断已提交").sceneCode(MEDIA_ACCOUNT_DIAGNOSIS_COMPLETED).templateCode("ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS_COMPLETED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("阶段交付已完成").sceneCode(STUDENT_DELIVERY_COMPLETED).templateCode("ZSJ_N269_STUDENT_DELIVERY_COMPLETED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("内容已登记发布").sceneCode(MEDIA_CONTENT_PUBLISHED).templateCode("ZSJ_N269_MEDIA_CONTENT_PUBLISHED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单待接单").sceneCode(MEDIA_TICKET_PENDING_ACCEPT).templateCode("ZSJ_N269_MEDIA_TICKET_PENDING_ACCEPT")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单待核对").sceneCode(MEDIA_TICKET_PENDING_CHECK).templateCode("ZSJ_N269_MEDIA_TICKET_PENDING_CHECK")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单核对通过").sceneCode(MEDIA_TICKET_APPROVED).templateCode("ZSJ_N269_MEDIA_TICKET_APPROVED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单返工").sceneCode(MEDIA_TICKET_REJECTED).templateCode("ZSJ_N269_MEDIA_TICKET_REJECTED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("指定拍剪工单被拒接").sceneCode(MEDIA_TICKET_ASSIGNMENT_REJECTED).templateCode("ZSJ_N269_MEDIA_TICKET_ASSIGNMENT_REJECTED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("公共池拍剪工单已被抢单").sceneCode(MEDIA_TICKET_CLAIMED).templateCode("ZSJ_N269_MEDIA_TICKET_CLAIMED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("内容验收通过").sceneCode(MEDIA_CONTENT_APPROVED).templateCode("ZSJ_N269_MEDIA_CONTENT_APPROVED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("内容验收退回").sceneCode(MEDIA_CONTENT_REJECTED).templateCode("ZSJ_N269_MEDIA_CONTENT_REJECTED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("账号换绑通过").sceneCode(MEDIA_ACCOUNT_REBIND_APPROVED).templateCode("ZSJ_N269_MEDIA_ACCOUNT_REBIND_APPROVED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("账号换绑驳回").sceneCode(MEDIA_ACCOUNT_REBIND_REJECTED).templateCode("ZSJ_N269_MEDIA_ACCOUNT_REBIND_REJECTED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("账号状态维护变更").sceneCode(MEDIA_ACCOUNT_MAINTENANCE_CHANGED).templateCode("ZSJ_N269_MEDIA_ACCOUNT_MAINTENANCE_CHANGED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("账号周期诊断提醒").sceneCode(MEDIA_ACCOUNT_DIAGNOSIS).templateCode("ZSJ_N269_MEDIA_ACCOUNT_DIAGNOSIS")
                .recipientRoles(List.of("assignee")).actionType("business_detail").timingStage("due").timingOffsetMinutes(0).build(),
            NotifyDefaultRuleReqDTO.builder().name("账号交付已延期").sceneCode(STUDENT_DELIVERY_DEFERRED).templateCode("ZSJ_N269_STUDENT_DELIVERY_DEFERRED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("S0-S6交付确认待填写").sceneCode(STUDENT_DELIVERY_CONFIRMATION).templateCode("ZSJ_N269_STUDENT_DELIVERY_CONFIRMATION")
                .recipientRoles(List.of("assignee")).actionType("business_detail").timingStage("due").timingOffsetMinutes(0).build(),
            NotifyDefaultRuleReqDTO.builder().name("定位待运营复核").sceneCode(MEDIA_POSITIONING_OPERATOR_REVIEW).templateCode("ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REVIEW")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("定位运营退回").sceneCode(MEDIA_POSITIONING_OPERATOR_REJECTED).templateCode("ZSJ_N269_MEDIA_POSITIONING_OPERATOR_REJECTED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("学员已确认定位").sceneCode(MEDIA_POSITIONING_STUDENT_CONFIRMED).templateCode("ZSJ_N269_MEDIA_POSITIONING_STUDENT_CONFIRMED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("学员已拒绝定位").sceneCode(MEDIA_POSITIONING_STUDENT_REJECTED).templateCode("ZSJ_N269_MEDIA_POSITIONING_STUDENT_REJECTED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("学员运营指派通知").sceneCode("zsjos.student.operator_assigned").templateCode("ZSJ_N269_ZSJOS_STUDENT_OPERATOR_ASSIGNED")
                .recipientRoles(List.of("operator")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("内容批次已提交").sceneCode("zsjos.content_review.batch_submitted").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_BATCH_SUBMITTED")
                .recipientRoles(List.of("director")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("编导已审核内容").sceneCode("zsjos.content_review.director_item_decision").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_ITEM_DECISION")
                .recipientRoles(List.of("operator")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("编导审核通过").sceneCode("zsjos.content_review.director_completed").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_COMPLETED")
                .recipientRoles(List.of("operator","final_reviewer")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("编导审核驳回").sceneCode("zsjos.content_review.director_rejected").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_REJECTED")
                .recipientRoles(List.of("operator","submitter")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("终审已审核内容").sceneCode("zsjos.content_review.final_item_decision").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_ITEM_DECISION")
                .recipientRoles(List.of("operator","director")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("终审通过").sceneCode("zsjos.content_review.final_completed").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_COMPLETED")
                .recipientRoles(List.of("operator","director","submitter")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("终审驳回").sceneCode("zsjos.content_review.final_rejected").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_REJECTED")
                .recipientRoles(List.of("operator","director","submitter")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("审核超时提醒").sceneCode("zsjos.content_review.review_timeout_reminder").templateCode("ZSJ_N269_ZSJOS_CONTENT_REVIEW_REVIEW_TIMEOUT_REMINDER")
                .recipientRoles(List.of("director","final_reviewer")).actionType("business_detail").timingStage("overdue").timingOffsetMinutes(1440).build(),
            NotifyDefaultRuleReqDTO.builder().name("收到指定工单").sceneCode("zsjos.work_order.assigned").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_ASSIGNED")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("候选池有新工单").sceneCode("zsjos.work_order.pool_available").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_POOL_AVAILABLE")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单已接单").sceneCode("zsjos.work_order.taken").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_TAKEN")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单被拒绝").sceneCode("zsjos.work_order.rejected").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_REJECTED")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单待验收").sceneCode("zsjos.work_order.review_requested").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_REVIEW_REQUESTED")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单已打回重做").sceneCode("zsjos.work_order.reworked").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_REWORKED")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单已验收通过").sceneCode("zsjos.work_order.completed").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_COMPLETED")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单不合格终止").sceneCode("zsjos.work_order.terminated").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_TERMINATED")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工单已撤回").sceneCode("zsjos.work_order.withdrawn").templateCode("ZSJ_N269_ZSJOS_WORK_ORDER_WITHDRAWN")
                .recipientRoles(List.of("recipient")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工作任务分派").sceneCode("work_task_assigned").templateCode("ZSJ_N269_WORK_TASK_ASSIGNED")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工作任务提醒").sceneCode("work_task_reminder").templateCode("ZSJ_N269_WORK_TASK_REMINDER")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工作任务首次逾期").sceneCode("work_task_overdue").templateCode("ZSJ_N269_WORK_TASK_OVERDUE")
                .recipientRoles(List.of("assignee")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("完成汇报待确认").sceneCode("work_task_report_submitted").templateCode("ZSJ_N269_WORK_TASK_REPORT_SUBMITTED")
                .recipientRoles(List.of("confirmer")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("任务确认完成").sceneCode("work_task_confirm_approved").templateCode("ZSJ_N269_WORK_TASK_CONFIRM_APPROVED")
                .recipientRoles(List.of("assignee","assigner")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("完成汇报退回").sceneCode("work_task_confirm_rejected").templateCode("ZSJ_N269_WORK_TASK_CONFIRM_REJECTED")
                .recipientRoles(List.of("assignee","assigner")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工作任务调整").sceneCode("work_task_adjusted").templateCode("ZSJ_N269_WORK_TASK_ADJUSTED")
                .recipientRoles(List.of("assignee","confirmer")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("工作任务取消").sceneCode("work_task_cancelled").templateCode("ZSJ_N269_WORK_TASK_CANCELLED")
                .recipientRoles(List.of("assignee","confirmer")).actionType("business_detail").build(),
            NotifyDefaultRuleReqDTO.builder().name("计划待总结").sceneCode("work_plan_summary_ready").templateCode("ZSJ_N269_WORK_PLAN_SUMMARY_READY")
                .recipientRoles(List.of("plan_owner")).actionType("business_detail").build()
        );
    }
}
