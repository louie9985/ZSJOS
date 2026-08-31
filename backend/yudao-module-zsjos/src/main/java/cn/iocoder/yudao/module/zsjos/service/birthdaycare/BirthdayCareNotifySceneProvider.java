package cn.iocoder.yudao.module.zsjos.service.birthdaycare;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.service.birthdaycare.BirthdayCareConstants.ROLE_RECIPIENT;
import static cn.iocoder.yudao.module.zsjos.service.birthdaycare.BirthdayCareConstants.SCENE;

@Component
public class BirthdayCareNotifySceneProvider implements NotifySceneProvider {
    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO(SCENE, "员工生日关怀", List.of(
                new NotifySceneVariableRespDTO("employee.name", "员工姓名", false),
                new NotifySceneVariableRespDTO("employee.department", "员工部门", false),
                new NotifySceneVariableRespDTO("employee.birthday", "生日月日", false)),
                List.of(new NotifySceneRoleRespDTO(ROLE_RECIPIENT, "生日关怀接收人")),
                List.of(NotifyActionType.MESSAGE_DETAIL), false));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        if (!roles.contains(ROLE_RECIPIENT)) return Set.of();
        Object userId = event.getPayload().get("recipientUserId");
        if (!(userId instanceof Number number)) return Set.of();
        return Set.of(NotifyRecipientDTO.admin(number.longValue()));
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("employee.name", event.getPayload().get("employeeName"));
        variables.put("employee.department", event.getPayload().get("departmentName"));
        variables.put("employee.birthday", event.getPayload().get("birthday"));
        return variables;
    }
}
