package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRoleRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneVariableRespDTO;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FeedbackNotifySceneProvider implements NotifySceneProvider {

    private static final String ROLE_DISPATCHER = "dispatcher";
    private static final String ROLE_HANDLER = "handler";
    private static final String ROLE_SUBMITTER = "submitter";

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(
                scene(FeedbackConstants.NOTIFY_SCENE_READY_FOR_HANDLING, "新反馈待处理", ROLE_DISPATCHER),
                scene("zsjos.feedback.employee_replied", "员工补充反馈", ROLE_HANDLER),
                scene("zsjos.feedback.admin_replied", "反馈有新回复", ROLE_SUBMITTER),
                scene("zsjos.feedback.completed", "反馈处理完成", ROLE_SUBMITTER),
                scene("zsjos.feedback.survey_requested", "反馈满意度调研", ROLE_SUBMITTER));
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();
        if (roles.contains(ROLE_SUBMITTER)) addSubmitter(recipients, payload);
        if (roles.contains(ROLE_DISPATCHER)
                && payload.get("dispatcherUserIds") instanceof Collection<?> dispatcherIds) {
            dispatcherIds.forEach(id -> addUser(recipients, id));
        }
        if (roles.contains(ROLE_HANDLER)) {
            Object assignee = payload.get("assigneeUserId");
            if (assignee instanceof Number number && number.longValue() > 0) {
                recipients.add(NotifyRecipientDTO.admin(number.longValue()));
            } else if (payload.get("dispatcherUserIds") instanceof Collection<?> dispatcherIds) {
                dispatcherIds.forEach(id -> addUser(recipients, id));
            }
        }
        return recipients;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("feedback.id", event.getBizId());
        variables.put("event.time", event.getOccurredAt());
        if (event.getPayload() != null) variables.putAll(event.getPayload());
        return variables;
    }

    private NotifySceneRespDTO scene(String code, String name, String role) {
        return new NotifySceneRespDTO(code, name,
                List.of(
                        variable("feedback.id", "反馈内部编号"),
                        variable("feedbackNo", "反馈编号"),
                        variable("feedbackTitle", "反馈标题"),
                        variable("deepLink", "反馈详情链接"),
                        variable("event.time", "发生时间")),
                List.of(new NotifySceneRoleRespDTO(role,
                        roleName(role))),
                List.of(NotifyActionType.BUSINESS_DETAIL), false);
    }

    private String roleName(String role) {
        return switch (role) {
            case ROLE_DISPATCHER -> "当前有效分派负责人";
            case ROLE_HANDLER -> "当前处理人或分派负责人";
            default -> "反馈提交人";
        };
    }

    private NotifySceneVariableRespDTO variable(String key, String name) {
        return new NotifySceneVariableRespDTO(key, name, false);
    }

    private void addUser(Set<NotifyRecipientDTO> recipients, Object value) {
        if (value instanceof Number number && number.longValue() > 0) {
            recipients.add(NotifyRecipientDTO.admin(number.longValue()));
        } else if (value != null) {
            long id = Long.parseLong(String.valueOf(value));
            if (id > 0) recipients.add(NotifyRecipientDTO.admin(id));
        }
    }

    private void addSubmitter(Set<NotifyRecipientDTO> recipients, Map<String, Object> payload) {
        Long id = parseId(payload.get("submitterUserId"));
        if (id == null) return;
        if (FeedbackConstants.SUBJECT_PARTNER_ACCOUNT.equals(payload.get("submitterSubjectType"))) {
            recipients.add(NotifyRecipientDTO.partner(id));
        } else {
            recipients.add(NotifyRecipientDTO.admin(id));
        }
    }

    private Long parseId(Object value) {
        if (value instanceof Number number && number.longValue() > 0) return number.longValue();
        if (value == null) return null;
        long id = Long.parseLong(String.valueOf(value));
        return id > 0 ? id : null;
    }
}
