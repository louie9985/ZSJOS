package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BpmNotifySceneProvider implements NotifySceneProvider {

    private static final String TARGET_USER = "target_user";

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene("bpm.process.approved", "流程审批通过"),
                scene("bpm.process.rejected", "流程审批拒绝"),
                scene("bpm.task.assigned", "审批任务待处理"),
                scene("bpm.task.timeout", "审批任务超时"));
    }

    private NotifySceneRespDTO scene(String code, String name) {
        return new NotifySceneRespDTO(code, name,
                List.of(new NotifySceneVariableRespDTO("processInstanceName", "流程名称", false),
                        new NotifySceneVariableRespDTO("taskName", "任务名称", false),
                        new NotifySceneVariableRespDTO("startUserNickname", "发起人", false),
                        new NotifySceneVariableRespDTO("reason", "审批原因", false),
                        new NotifySceneVariableRespDTO("detailUrl", "详情地址", false)),
                List.of(new NotifySceneRoleRespDTO(TARGET_USER, "事件接收人")),
                List.of(NotifyActionType.NONE, NotifyActionType.MESSAGE_DETAIL));
    }

    @Override
    public Set<Long> resolveRecipients(NotifyBusinessEvent event, Set<String> recipientRoles) {
        if (!recipientRoles.contains(TARGET_USER)) {
            return Set.of();
        }
        Object value = event.getPayload().get("targetUserId");
        return value instanceof Number number ? Set.of(number.longValue()) : Set.of();
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, Long recipientUserId) {
        return event.getPayload();
    }
}
