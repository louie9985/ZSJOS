package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskSignTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;

/** 普通成交审核的驳回原因约束同时覆盖业务接口和通用 BPM 入口。 */
@Component
public class SalesOrderTaskActionValidator implements BpmTaskActionValidator {
    @Resource private SalesOrderMapper orderMapper;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private SalesOrderApprovalRoundMapper roundMapper;
    @Resource private SalesOrderObjectPermissionService orderPermissionService;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private CashbackService cashbackService;

    @Override
    public Boolean approvalReasonRequired(BpmTaskActionContext context) {
        if (FINANCE_PROCESS_DEFINITION_KEY.equals(context.getProcessDefinitionKey())) {
            return StrUtil.isNotBlank(context.getParentTaskId());
        }
        if (!PROCESS_DEFINITION_KEY.equals(context.getProcessDefinitionKey())
                || !(TASK_REGISTRATION.equals(context.getTaskDefinitionKey()) || TASK_FINANCE.equals(context.getTaskDefinitionKey()))) {
            return null;
        }
        // 新旧普通订单统一选填；主管加签任务仍必填，不能继承普通节点的放宽规则。
        return StrUtil.isNotBlank(context.getParentTaskId());
    }

    @Override
    public void validate(BpmTaskActionContext context) {
        if (FINANCE_PROCESS_DEFINITION_KEY.equals(context.getProcessDefinitionKey())) {
            if ("StartUserNode".equals(context.getTaskDefinitionKey()) && ACTION_APPROVE.equals(context.getAction())
                    && context.getUserId() != null && String.valueOf(context.getUserId()).equals(context.getAssignee())) {
                return;
            }
            validateFinanceAction(context);
            return;
        }
        if (!PROCESS_DEFINITION_KEY.equals(context.getProcessDefinitionKey())
                || !(TASK_REGISTRATION.equals(context.getTaskDefinitionKey()) || TASK_FINANCE.equals(context.getTaskDefinitionKey()))) {
            return;
        }
        lockLegacyRoundAgainstMigration(context);
        if (TASK_FINANCE.equals(context.getTaskDefinitionKey()) && context.getProcessInstanceId() != null) {
            var round = roundMapper.selectByProcessInstanceId(context.getProcessInstanceId());
            if (round != null && Boolean.FALSE.equals(round.getSupervisorConfirmationEnabled())) {
                validateFinanceAction(context);
                return;
            }
        }
        if ((ACTION_REJECT.equals(context.getAction())
                    || (ACTION_APPROVE.equals(context.getAction()) && StrUtil.isNotBlank(context.getParentTaskId())))
                && StrUtil.isBlank(context.getReason())) {
            if (ACTION_REJECT.equals(context.getAction())) throw exception(SALES_ORDER_REJECT_REASON_REQUIRED);
            // 主管子任务沿用原意见必填规则，不继承普通中心的新选填策略。
            throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_SUPERVISOR_REASON_REQUIRED);
        }
    }

    private void lockLegacyRoundAgainstMigration(BpmTaskActionContext context) {
        if (context.getProcessInstanceId() == null) return;
        var located = roundMapper.selectByProcessInstanceId(context.getProcessInstanceId());
        if (located == null) return;
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var order = orderMapper.selectByIdForUpdate(located.getOrderId(), tenantId);
        if (order == null || !Objects.equals(order.getCurrentApprovalRoundId(), located.getId())) {
            throw exception(SALES_ORDER_VERSION_CONFLICT);
        }
        var round = roundMapper.selectByIdForUpdate(located.getId(), tenantId);
        if (round == null || !ROUND_PENDING.equals(round.getStatus())) {
            throw exception(SALES_ORDER_ALREADY_HANDLED);
        }
        if (TASK_REGISTRATION.equals(context.getTaskDefinitionKey()) &&
                processTaskApi.getCommittedNodeTasks(context.getProcessInstanceId(), TASK_REGISTRATION).stream()
                        .noneMatch(task -> context.getTaskId().equals(task.id()))) {
            throw exception(SALES_ORDER_ALREADY_HANDLED);
        }
    }

    private void validateFinanceAction(BpmTaskActionContext context) {
        Long userId = context.getUserId();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        boolean signChild = StrUtil.isNotBlank(context.getParentTaskId());
        String action = context.getAction();
        if (userId == null || !Objects.equals(context.getAssignee(), String.valueOf(userId))
                || !Objects.equals(context.getTenantId(), String.valueOf(tenantId))
                || !BpmTaskStatusEnum.RUNNING.getStatus().equals(context.getTaskStatus())
                || !TASK_FINANCE.equals(context.getTaskDefinitionKey())
                || !(ACTION_APPROVE.equals(action) || ACTION_REJECT.equals(action) || (!signChild && ACTION_ADD_SIGN.equals(action)))) {
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        }
        if (signChild ? !permissionApi.hasAnyPermissions(userId, "bpm:task:update", PERMISSION_REVIEW)
                : !permissionApi.hasAnyPermissions(userId, PERMISSION_REVIEW) || !orderPermissionService.isFinanceCenterMember(userId)) {
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        }
        var located = roundMapper.selectByProcessInstanceId(context.getProcessInstanceId());
        if (located == null) throw exception(SALES_ORDER_VERSION_CONFLICT);
        var order = orderMapper.selectByIdForUpdate(located.getOrderId(), tenantId);
        var round = roundMapper.selectByIdForUpdate(located.getId(), tenantId);
        if (order == null || round == null || !Objects.equals(order.getCurrentApprovalRoundId(), round.getId())
                || !Objects.equals(round.getProcessInstanceId(), context.getProcessInstanceId())
                || !Objects.equals(context.getBusinessKey(), BUSINESS_KEY_PREFIX + order.getId())
                || !STATUS_PENDING_APPROVAL.equals(order.getStatus()) || !ROUND_PENDING.equals(round.getStatus())) {
            throw exception(SALES_ORDER_VERSION_CONFLICT);
        }
        validateCurrentTaskAndRequiredSign(context);
        String reason = StrUtil.trimToEmpty(context.getReason());
        if (reason.length() > 1000) throw exception(SALES_ORDER_PERMISSION_DENIED);
        if (ACTION_REJECT.equals(action)) {
            if (reason.isEmpty()) throw exception(SALES_ORDER_REJECT_REASON_REQUIRED);
            cashbackService.assertOrderRejectable(order.getId());
        } else if (signChild && reason.isEmpty()) {
            throw exception(SALES_ORDER_SUPERVISOR_REASON_REQUIRED);
        } else if (ACTION_ADD_SIGN.equals(action)) {
            validateSignCandidates(context);
        }
    }

    private void validateCurrentTaskAndRequiredSign(BpmTaskActionContext context) {
        var tasks = processTaskApi.getCommittedNodeTasks(context.getProcessInstanceId(), TASK_FINANCE);
        var current = tasks.stream().filter(task -> context.getTaskId().equals(task.id())).findFirst().orElse(null);
        if (current == null || !Objects.equals(current.assignee(), context.getAssignee())
                || !Objects.equals(current.scopeType(), context.getTaskScopeType())
                || !BpmTaskStatusEnum.RUNNING.getStatus().equals(current.status())) {
            throw exception(SALES_ORDER_ALREADY_HANDLED);
        }
        String currentParentId = current.parentTaskId() == null ? current.id() : current.parentTaskId();
        // An OR decision in another instance of this node would otherwise discard required sign children.
        if (tasks.stream().anyMatch(task -> task.parentTaskId() != null && !currentParentId.equals(task.parentTaskId()))) {
            throw exception(SALES_ORDER_FINANCE_SIGN_PENDING);
        }
    }

    private void validateSignCandidates(BpmTaskActionContext context) {
        if (StrUtil.isBlank(context.getReason())
                || !(BpmTaskSignTypeEnum.BEFORE.getType().equals(context.getSignType())
                    || BpmTaskSignTypeEnum.AFTER.getType().equals(context.getSignType()))
                || context.getSignUserIds() == null || context.getSignUserIds().isEmpty()) {
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        }
        var users = adminUserApi.getUserList(context.getSignUserIds());
        if (users.size() != context.getSignUserIds().size()
                || users.stream().anyMatch(user -> !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                    || (!permissionApi.hasAnyPermissions(user.getId(), PERMISSION_REVIEW)
                        && (!permissionApi.hasAnyPermissions(user.getId(), "bpm:task:update")
                            || !permissionApi.hasAnyPermissions(user.getId(), "bpm:task:query"))))) {
            throw exception(SALES_ORDER_PERMISSION_DENIED);
        }
    }
}
