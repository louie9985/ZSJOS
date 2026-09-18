package cn.iocoder.yudao.module.zsjos.service.bpm;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.bpm.vo.ZsjosBpmBusinessTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderApprovalTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants;
import cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackObjectPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderSupervisorConfirmationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.PROCESS_DEFINITION_KEY;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.FEEDBACK_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_APPEAL_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_PERMISSION_DENIED;

@Service
public class ZsjosBpmBusinessTaskTargetServiceImpl implements ZsjosBpmBusinessTaskTargetService {

    private static final String VIEW_DONE = "done";
    private static final String UNSUPPORTED_MESSAGE = "该流程暂未接入员工端业务审批页，请在完整 BPM 表单中处理。";
    /** 与 {@code FeedbackServiceImpl.createRequirementRound} 发起流程时的前缀保持一致。 */
    private static final String FEEDBACK_BUSINESS_KEY_PREFIX = "feedback:";

    @Resource private cn.iocoder.yudao.module.zsjos.service.material.MaterialApprovalService materialApprovalService;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private SalesOrderSupervisorConfirmationService salesOrderTargetService;
    @Resource private LeadAppealMapper leadAppealMapper;
    @Resource private FeedbackMapper feedbackMapper;
    @Resource private FeedbackObjectPermissionProvider permissionProvider;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;

    @Override
    public ZsjosBpmBusinessTaskTargetRespVO getTarget(String taskId, String view, Long userId) {
        boolean done = VIEW_DONE.equals(view);
        BpmTaskRespDTO task = done ? processTaskApi.getDoneTask(userId, taskId) : processTaskApi.getTodoTask(userId, taskId);
        if (task == null) {
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        }
        if (PROCESS_DEFINITION_KEY.equals(task.getProcessDefinitionKey())) {
            return salesOrderTarget(taskId, userId, done);
        }
        if (APPEAL_PROCESS_DEFINITION_KEY.equals(task.getProcessDefinitionKey())) {
            return leadAppealTarget(task, userId, done);
        }
        if (task.getProcessDefinitionKey() != null && java.util.Set.of("zsjos_viral_account_review", "zsjos_viral_content_review").contains(task.getProcessDefinitionKey())) {
            if (!permissionApi.hasAnyPermissions(userId, "zsjos:material-approval:query")) {
                throw exception(cn.iocoder.yudao.module.zsjos.service.material.MaterialApprovalErrors.INVALID_TASK);
            }
            Long versionId = cn.iocoder.yudao.module.zsjos.service.material.MaterialApprovalService.versionId(task);
            materialApprovalService.requireTask(versionId, taskId, done, userId);
            // Viral material tasks are handled by the dedicated approval page. The
            // management page cannot reconstruct a pending BPM task from a material id.
            ZsjosBpmBusinessTaskTargetRespVO target = supported("material", "/zsjos/material-library/approvals");
            target.getQuery().put("taskId", taskId);
            target.getQuery().put("versionId", versionId);
            target.getQuery().put("done", done);
            target.getQuery().put("typeCode", "zsjos_viral_account_review".equals(task.getProcessDefinitionKey()) ? "viral_account" : "viral_content");
            return target;
        }
        if (FeedbackConstants.PROCESS_DEFINITION_KEY.equals(task.getProcessDefinitionKey())) {
            return feedbackTarget(task, userId);
        }
        return unsupported();
    }

    /**
     * 需求反馈审批 → 员工端反馈页。
     *
     * <p>businessKey 是四段式 {@code feedback:{workOrderId}:round:{roundNo}}，第二段是
     * <b>workOrderId 不是 feedbackId</b>——按 feedbackId 去 selectById 会静默取到另一条无关的反馈。
     * 这里先用 workOrderId 换出唯一的 FeedbackDO（有 uk_tenant_work_order 唯一约束），
     * 与 {@code FeedbackContentProvider} 同一套解析口径。
     *
     * <p>权限复用 {@code read-approver}：只有这一轮次被指定的审批人能打开。
     * 拿不到目标就抛，前端据此提示"无权打开"，而不是跳过去再看一个空白页。
     */
    private ZsjosBpmBusinessTaskTargetRespVO feedbackTarget(BpmTaskRespDTO task, Long userId) {
        String workOrderId = BpmApprovalBusinessKey.idSegment(
                BpmApprovalBusinessKey.strip(FEEDBACK_BUSINESS_KEY_PREFIX, task.getBusinessKey()));
        FeedbackDO feedback = workOrderId == null ? null
                : feedbackMapper.selectByWorkOrderId(Long.valueOf(workOrderId));
        if (feedback == null || !permissionProvider.hasPermission(
                feedback.getId(), FeedbackObjectPermissionProvider.ACTION_READ_APPROVER, userId)) {
            throw exception(FEEDBACK_PERMISSION_DENIED);
        }
        ZsjosBpmBusinessTaskTargetRespVO target = supported("feedback", "/zsjos/feedback");
        target.getQuery().put("feedbackId", feedback.getId());
        return target;
    }

    private ZsjosBpmBusinessTaskTargetRespVO salesOrderTarget(String taskId, Long userId, boolean done) {
        SalesOrderApprovalTaskTargetRespVO source = salesOrderTargetService.getTaskTarget(taskId, userId, done);
        ZsjosBpmBusinessTaskTargetRespVO target = supported("sales_order", "/zsjos/sales-order-approvals");
        target.getQuery().put("workType", source.getWorkType());
        target.getQuery().put("orderId", source.getOrderId());
        target.getQuery().put("taskId", source.getTaskId());
        if (source.getConfirmationId() != null) {
            target.getQuery().put("confirmationId", source.getConfirmationId());
        }
        return target;
    }

    private ZsjosBpmBusinessTaskTargetRespVO leadAppealTarget(BpmTaskRespDTO task, Long userId, boolean done) {
        if (!APPEAL_TASK_DEFINITION_KEY.equals(task.getTaskDefinitionKey())) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        Long appealId = parseAppealId(task.getBusinessKey());
        LeadAppealDO appeal = appealId == null ? null : leadAppealMapper.selectById(appealId);
        if (appeal == null || !Objects.equals(task.getProcessInstanceId(), appeal.getProcessInstanceId())
                || !Objects.equals(task.getBusinessKey(), APPEAL_BUSINESS_KEY_PREFIX + appealId)
                || !hasStagePermission(appeal, userId) || !canReviewSnapshot(appeal, userId)) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        ZsjosBpmBusinessTaskTargetRespVO target = supported("lead_appeal", "/zsjos/appeals");
        target.getQuery().put("appealId", appeal.getId());
        target.getQuery().put("leadId", appeal.getLeadId());
        target.getQuery().put("handled", done || !isReviewing(appeal.getStatus()));
        return target;
    }

    private boolean hasStagePermission(LeadAppealDO appeal, Long userId) {
        String permission;
        if (APPEAL_STAGE_SALES_MANAGER.equals(appeal.getReviewStage())) {
            permission = PERMISSION_APPEAL_REVIEW_SALES_MANAGER;
        } else if (APPEAL_STAGE_QUALITY.equals(appeal.getReviewStage())) {
            permission = PERMISSION_APPEAL_REVIEW_QUALITY;
        } else if (APPEAL_STAGE_CHAIRMAN.equals(appeal.getReviewStage())) {
            permission = PERMISSION_APPEAL_REVIEW_CHAIRMAN;
        } else {
            permission = null;
        }
        return permission != null && permissionApi.hasAnyPermissions(userId, permission);
    }

    private boolean canReviewSnapshot(LeadAppealDO appeal, Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            return false;
        }
        String snapshot = appeal.getReviewerUserIdsSnapshot();
        if (snapshot == null) {
            return true;
        }
        List<Long> reviewerIds = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseArray(snapshot, Long.class);
        return CollUtil.isNotEmpty(reviewerIds) && reviewerIds.contains(userId);
    }

    private Long parseAppealId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(APPEAL_BUSINESS_KEY_PREFIX)) {
            return null;
        }
        try { return Long.valueOf(businessKey.substring(APPEAL_BUSINESS_KEY_PREFIX.length())); }
        catch (NumberFormatException ignored) { return null; }
    }

    private boolean isReviewing(String status) {
        return APPEAL_STATUS_SALES_MANAGER_REVIEWING.equals(status)
                || APPEAL_STATUS_QUALITY_REVIEWING.equals(status)
                || APPEAL_STATUS_CHAIRMAN_REVIEWING.equals(status);
    }

    private ZsjosBpmBusinessTaskTargetRespVO supported(String bizType, String route) {
        ZsjosBpmBusinessTaskTargetRespVO target = new ZsjosBpmBusinessTaskTargetRespVO();
        target.setSupported(true); target.setBizType(bizType); target.setRoute(route);
        return target;
    }

    private ZsjosBpmBusinessTaskTargetRespVO unsupported() {
        ZsjosBpmBusinessTaskTargetRespVO target = new ZsjosBpmBusinessTaskTargetRespVO();
        target.setSupported(false); target.setBizType("unsupported"); target.setMessage(UNSUPPORTED_MESSAGE);
        target.setQuery(new LinkedHashMap<>());
        return target;
    }
}
