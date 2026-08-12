package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_PLAN_PERMISSION_DENIED;

@Component
public class WorkPlanObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private WorkPlanMapper planMapper;
    @Resource private WorkTaskMapper taskMapper;
    @Resource private PermissionApi permissionApi;

    @Override
    public String getBizType() {
        return BIZ_TYPE_WORK_PLAN;
    }

    @Override
    public boolean hasPermission(Long planId, String action, Long userId) {
        WorkPlanDO plan = planMapper.selectById(planId);
        if (plan == null) return false;
        boolean full = hasFullPlanAccess(plan, userId);
        if ("read".equals(action)) {
            return full || taskMapper.selectListByPlanId(planId).stream().anyMatch(task -> isRelated(task, userId));
        }
        return switch (action) {
            case "update", "publish", "assign", "cancel" -> full;
            case "close" -> Objects.equals(plan.getOwnerUserId(), userId);
            default -> false;
        };
    }

    @Override
    public void check(Long bizId, String action, Long userId) {
        if (!hasPermission(bizId, action, userId)) throw exception(WORK_PLAN_PERMISSION_DENIED);
    }

    public boolean hasFullPlanAccess(WorkPlanDO plan, Long userId) {
        return Objects.equals(plan.getCreatorUserId(), userId) || Objects.equals(plan.getOwnerUserId(), userId)
                || inDeptScope(plan.getOwnerDeptId(), userId)
                || taskMapper.selectListByPlanId(plan.getId()).stream().anyMatch(task -> inDeptScope(task.getAssigneeDeptId(), userId));
    }

    public boolean hasTaskPermission(WorkTaskDO task, String action, Long userId) {
        WorkPlanDO plan = task.getPlanId() == null ? null : planMapper.selectById(task.getPlanId());
        boolean full = plan != null && hasFullPlanAccess(plan, userId);
        return switch (action) {
            case "read" -> full || isRelated(task, userId);
            case "report", "decompose" -> Objects.equals(task.getAssigneeUserId(), userId);
            case "confirm" -> Objects.equals(task.getConfirmerUserId(), userId);
            case "assign", "cancel" -> full || Objects.equals(task.getAssignerUserId(), userId);
            default -> false;
        };
    }

    public List<String> availablePlanActions(WorkPlanDO plan, boolean summaryReady, Long userId) {
        List<String> actions = new ArrayList<>();
        if (PLAN_DRAFT.equals(plan.getStatus())) {
            addIf(actions, "update", hasPermission(plan.getId(), "update", userId), userId, PERMISSION_UPDATE);
            addIf(actions, "publish", hasPermission(plan.getId(), "publish", userId), userId, PERMISSION_PUBLISH);
            addIf(actions, "cancel", hasPermission(plan.getId(), "cancel", userId), userId, PERMISSION_CANCEL);
        } else if (PLAN_ACTIVE.equals(plan.getStatus())) {
            addIf(actions, "update", hasPermission(plan.getId(), "update", userId), userId, PERMISSION_UPDATE);
            addIf(actions, "assign", hasPermission(plan.getId(), "assign", userId), userId, PERMISSION_ASSIGN);
            addIf(actions, "cancel", hasPermission(plan.getId(), "cancel", userId), userId, PERMISSION_CANCEL);
            addIf(actions, "close", hasPermission(plan.getId(), "close", userId), userId, PERMISSION_CLOSE);
        }
        return actions;
    }

    public List<String> availableTaskActions(WorkTaskDO task, boolean blockedByChildren, Long userId) {
        List<String> actions = new ArrayList<>();
        if (TASK_PENDING.equals(task.getStatus())) {
            addIf(actions, "assign", hasTaskPermission(task, "assign", userId), userId, PERMISSION_ASSIGN);
            addIf(actions, "decompose", hasTaskPermission(task, "decompose", userId), userId, PERMISSION_DECOMPOSE);
            addIf(actions, "complete", hasTaskPermission(task, "report", userId), userId, PERMISSION_COMPLETE);
            addIf(actions, "cancel", hasTaskPermission(task, "cancel", userId), userId, PERMISSION_CANCEL);
        } else if (TASK_AWAITING_CONFIRMATION.equals(task.getStatus())) {
            addIf(actions, "review", hasTaskPermission(task, "confirm", userId), userId, PERMISSION_REVIEW);
        }
        return actions;
    }

    public boolean isRelated(WorkTaskDO task, Long userId) {
        return Objects.equals(task.getAssigneeUserId(), userId) || Objects.equals(task.getConfirmerUserId(), userId)
                || Objects.equals(task.getAssignerUserId(), userId) || inDeptScope(task.getAssigneeDeptId(), userId);
    }

    private boolean inDeptScope(Long deptId, Long userId) {
        DeptDataPermissionRespDTO scope = permissionApi.getDeptDataPermission(userId);
        return scope != null && (Boolean.TRUE.equals(scope.getAll())
                || deptId != null && scope.getDeptIds() != null && scope.getDeptIds().contains(deptId));
    }

    private void addIf(List<String> actions, String action, boolean objectAllowed, Long userId, String permission) {
        if (objectAllowed && permissionApi.hasAnyPermissions(userId, permission)) actions.add(action);
    }
}
