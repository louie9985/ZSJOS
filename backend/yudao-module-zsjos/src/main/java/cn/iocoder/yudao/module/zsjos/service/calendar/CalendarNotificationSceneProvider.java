package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

/** Fixed-recipient calendar scenes. Recipient IDs are frozen by the calendar command. */
@Component
public class CalendarNotificationSceneProvider implements NotifySceneProvider {
    public static final String EXAM = "zsjos.calendar.exam";
    public static final String COURSE = "zsjos.calendar.course";

    @Override public List<NotifySceneRespDTO> getScenes() {
        return List.of(scene(EXAM, "考期日历通知"), scene(COURSE, "课程日历通知"));
    }
    @Override public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        return Set.of(); // Calendar events must carry validated FIXED identities, never role-derived recipients.
    }
    @Override public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (event.getPayload() != null) result.putAll(event.getPayload());
        result.put("calendar.id", event.getBizId());
        return result;
    }
    private NotifySceneRespDTO scene(String code, String name) {
        return new NotifySceneRespDTO(code, name,
                List.of(new NotifySceneVariableRespDTO("calendar.title", "日历标题", false),
                        new NotifySceneVariableRespDTO("calendar.time", "日历时间", false),
                        new NotifySceneVariableRespDTO("calendar.remark", "备注", false),
                        new NotifySceneVariableRespDTO("calendar.type", "日历类型", false),
                        new NotifySceneVariableRespDTO("calendar.eventType", "通知事件", false)),
                List.of(new NotifySceneRoleRespDTO("recipient", "通知接收人")),
                List.of(NotifyActionType.NONE), false);
    }
}
