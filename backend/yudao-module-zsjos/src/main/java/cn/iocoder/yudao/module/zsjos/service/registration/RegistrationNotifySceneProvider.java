package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Component
@Slf4j
public class RegistrationNotifySceneProvider implements NotifySceneProvider {

    @Resource private PermissionApi permissionApi;

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO(NOTIFY_SCENE_TASK_CREATED, "新报名履约任务", List.of(
                new NotifySceneVariableRespDTO("registration.caseId", "报名履约任务编号", false),
                new NotifySceneVariableRespDTO("order.id", "订单编号", false),
                new NotifySceneVariableRespDTO("order.no", "订单号", false),
                new NotifySceneVariableRespDTO("student.name", "学员姓名", true)),
                List.of(new NotifySceneRoleRespDTO(NOTIFY_ROLE_POOL_HANDLERS, "报名履约公共池处理人")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        if (!roles.contains(NOTIFY_ROLE_POOL_HANDLERS)) return Set.of();
        Set<Long> userIds = permissionApi.getEnabledUserIdsByPermission(PERMISSION_QUERY_POOL);
        if (userIds.isEmpty()) {
            log.warn("[resolveRecipients][tenantId({}) registrationCaseId({}) has no enabled pool handler]",
                    event.getTenantId(), event.getBizId());
        }
        return userIds.stream().map(NotifyRecipientDTO::admin)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registration.caseId", event.getPayload().get("registrationCaseId"));
        result.put("order.id", event.getPayload().get("orderId"));
        result.put("order.no", event.getPayload().get("orderNo"));
        result.put("student.name", event.getPayload().get("studentName"));
        return result;
    }
}
