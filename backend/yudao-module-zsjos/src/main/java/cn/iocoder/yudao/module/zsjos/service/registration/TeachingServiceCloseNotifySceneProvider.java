package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class TeachingServiceCloseNotifySceneProvider implements NotifySceneProvider {
    public static final String SCENE = "zsjos.student.service_closed";
    public static final String SUPERVISOR = "delivery_supervisor";
    private static final List<String> VARIABLES = List.of("student.name","course.name","planner.name","close.reason","close.time");

    @Override public List<NotifySceneRespDTO> getScenes() {
        return List.of(new NotifySceneRespDTO(SCENE,"学员教学服务关闭",List.of(
                new NotifySceneVariableRespDTO("student.name","学员姓名",false),
                new NotifySceneVariableRespDTO("course.name","课程名称",false),
                new NotifySceneVariableRespDTO("planner.name","学习规划师",false),
                new NotifySceneVariableRespDTO("close.reason","关闭原因",false),
                new NotifySceneVariableRespDTO("close.time","关闭时间",false)),
                List.of(new NotifySceneRoleRespDTO(SUPERVISOR,"交付主管")),
                List.of(NotifyActionType.MESSAGE_DETAIL,NotifyActionType.BUSINESS_DETAIL),false));
    }
    @Override public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event,Set<String> roles) {
        Object supervisor = event.getPayload() == null ? null : event.getPayload().get("supervisorUserId");
        return roles.contains(SUPERVISOR) && supervisor instanceof Number number && number.longValue()>0
                ? Set.of(NotifyRecipientDTO.admin(number.longValue())) : Set.of();
    }
    @Override public Map<String,Object> resolveVariables(NotifyBusinessEvent event,NotifyRecipientDTO recipient) {
        var payload=event.getPayload() == null ? Map.<String,Object>of() : event.getPayload();
        var variables=new LinkedHashMap<String,Object>();
        VARIABLES.forEach(key -> variables.put(key,Objects.requireNonNullElse(payload.get(key),"")));
        return variables;
    }
}
