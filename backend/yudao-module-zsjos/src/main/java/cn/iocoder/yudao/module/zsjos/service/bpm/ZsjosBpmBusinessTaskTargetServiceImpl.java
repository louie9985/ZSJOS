package cn.iocoder.yudao.module.zsjos.service.bpm;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.bpm.vo.ZsjosBpmBusinessTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderApprovalTaskTargetRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderSupervisorConfirmationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.PROCESS_DEFINITION_KEY;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_APPEAL_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_PERMISSION_DENIED;

@Service
public class ZsjosBpmBusinessTaskTargetServiceImpl implements ZsjosBpmBusinessTaskTargetService {

    private static final String VIEW_DONE = "done";
    private static final String UNSUPPORTED_MESSAGE = "该流程暂未接入员工端业务审批页，请在完整 BPM 表单中处理。";

    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private SalesOrderSupervisorConfirmationService salesOrderTargetService;
    @Resource private LeadAppealMapper leadAppealMapper;
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
        return unsupported();
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
