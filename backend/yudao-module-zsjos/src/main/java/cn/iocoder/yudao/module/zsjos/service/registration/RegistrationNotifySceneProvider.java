package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Component
@Slf4j
public class RegistrationNotifySceneProvider implements NotifySceneProvider {

    @Resource private PermissionApi permissionApi;
    @Resource private SalesOrderApprovalConfigMapper approvalConfigMapper;
    @Resource private DeptApi deptApi;
    @Resource private AdminUserApi adminUserApi;

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO(NOTIFY_SCENE_TASK_CREATED, "新报名履约任务", List.of(
                new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                new NotifySceneVariableRespDTO("order.id", "订单编号", false),
                new NotifySceneVariableRespDTO("order.no", "订单号", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_POOL_HANDLERS, "报名履约公共池处理人")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false),
                new NotifySceneRespDTO(NOTIFY_SCENE_PLANNER_ASSIGNED, "学习规划师分配", List.of(
                        new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                        new NotifySceneVariableRespDTO("order.no", "订单号", false),
                        new NotifySceneVariableRespDTO("lead.no", "客资编号", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_STUDY_PLANNER, "学习规划师")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false),
                new NotifySceneRespDTO(NOTIFY_SCENE_DIRECTOR_ASSIGNED, "编导学员分配", List.of(
                        new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                        new NotifySceneVariableRespDTO("order.no", "订单号", false),
                        new NotifySceneVariableRespDTO("lead.no", "客资编号", false)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_CONTENT_DIRECTOR, "编导")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        if (roles.contains(NOTIFY_ROLE_STUDY_PLANNER)) {
            Object plannerId = payload.get("studyPlannerUserId");
            if (plannerId instanceof Number number) return Set.of(NotifyRecipientDTO.admin(number.longValue()));
            return Set.of();
        }
        if (roles.contains(NOTIFY_ROLE_CONTENT_DIRECTOR)) {
            Object directorId = payload.get("contentDirectorUserId");
            if (directorId instanceof Number number) return Set.of(NotifyRecipientDTO.admin(number.longValue()));
            return Set.of();
        }
        if (!roles.contains(NOTIFY_ROLE_POOL_HANDLERS)) return Set.of();
        Set<Long> permissionUserIds = permissionApi.getEnabledUserIdsByPermission(PERMISSION_QUERY_POOL);
        SalesOrderApprovalConfigDO config = approvalConfigMapper.selectCurrent();
        Long registrationDeptId = config == null ? null : config.getRegistrationDeptId();
        if (registrationDeptId == null || permissionUserIds.isEmpty()) {
            log.warn("[resolveRecipients][tenantId({}) registrationCaseId({}) has no registration department scope or permission user]",
                    event.getTenantId(), event.getBizId());
            return Set.of();
        }
        Set<Long> deptIds = new LinkedHashSet<>();
        deptIds.add(registrationDeptId);
        deptApi.getChildDeptList(registrationDeptId).stream().map(item -> item.getId())
                .filter(java.util.Objects::nonNull).forEach(deptIds::add);
        Set<Long> scopedUserIds = new LinkedHashSet<>();
        adminUserApi.getUserListByDeptIds(deptIds).stream().map(user -> user.getId())
                .filter(java.util.Objects::nonNull).forEach(scopedUserIds::add);
        scopedUserIds.retainAll(permissionUserIds);
        Set<Long> userIds = scopedUserIds;
        if (userIds.isEmpty()) {
            log.warn("[resolveRecipients][tenantId({}) registrationCaseId({}) has no enabled pool handler]",
                    event.getTenantId(), event.getBizId());
        }
        return userIds.stream().map(NotifyRecipientDTO::admin)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registration.caseId", payload.get("registrationCaseId"));
        result.put("order.id", payload.get("orderId"));
        result.put("order.no", payload.get("orderNo"));
        result.put("lead.no", payload.get("leadNo"));
        result.put("student.name", payload.get("studentName"));
        return result;
    }
}
