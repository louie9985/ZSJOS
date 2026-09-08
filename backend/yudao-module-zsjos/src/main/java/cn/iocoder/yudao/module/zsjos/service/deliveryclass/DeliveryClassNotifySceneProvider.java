package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DeliveryClassNotifySceneProvider implements NotifySceneProvider {
    public static final String SCENE_OWNER_CHANGED = "zsjos_delivery_class_owner_changed";
    private static final String ROLE_PREVIOUS_OWNER = "previous_owner";
    private static final String ROLE_NEW_OWNER = "new_owner";

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO(SCENE_OWNER_CHANGED, "班级学员归属变更", List.of(
                new NotifySceneVariableRespDTO("service.id", "课程服务编号", false),
                new NotifySceneVariableRespDTO("class.id", "目标班级编号", false),
                new NotifySceneVariableRespDTO("transfer.reason", "变更原因", false)),
                List.of(new NotifySceneRoleRespDTO(ROLE_PREVIOUS_OWNER, "原学习规划师"),
                        new NotifySceneRoleRespDTO(ROLE_NEW_OWNER, "新学习规划师")),
                List.of(NotifyActionType.MESSAGE_DETAIL, NotifyActionType.BUSINESS_DETAIL), false));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();
        if (roles.contains(ROLE_PREVIOUS_OWNER)) addRecipient(recipients, payload.get("previousOwnerUserId"));
        if (roles.contains(ROLE_NEW_OWNER)) addRecipient(recipients, payload.get("newOwnerUserId"));
        return recipients;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("service.id", payload.get("serviceRelationId"));
        variables.put("class.id", payload.get("classId"));
        variables.put("transfer.reason", payload.get("reason"));
        return variables;
    }

    private void addRecipient(Set<NotifyRecipientDTO> recipients, Object value) {
        if (value instanceof Number number) recipients.add(NotifyRecipientDTO.admin(number.longValue()));
    }
}
