package cn.iocoder.yudao.module.zsjos.service.contentreview;

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
public class ContentReviewNotifySceneProvider implements NotifySceneProvider {

    private static final String ROLE_DIRECTOR = "director";
    private static final String ROLE_FINAL_REVIEWER = "final_reviewer";
    private static final String ROLE_OPERATOR = "operator";
    private static final String ROLE_SUBMITTER = "submitter";

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return List.of(
                scene("zsjos.content_review.batch_submitted", "内容批次已提交", ROLE_DIRECTOR),
                scene("zsjos.content_review.director_item_decision", "编导已审核内容", ROLE_OPERATOR),
                scene("zsjos.content_review.director_completed", "编导审核通过", ROLE_OPERATOR, ROLE_FINAL_REVIEWER),
                scene("zsjos.content_review.director_rejected", "编导审核驳回", ROLE_OPERATOR, ROLE_SUBMITTER),
                scene("zsjos.content_review.final_item_decision", "终审已审核内容", ROLE_OPERATOR, ROLE_DIRECTOR),
                scene("zsjos.content_review.final_completed", "终审通过", ROLE_OPERATOR, ROLE_DIRECTOR, ROLE_SUBMITTER),
                scene("zsjos.content_review.final_rejected", "终审驳回", ROLE_OPERATOR, ROLE_DIRECTOR, ROLE_SUBMITTER),
                scene("zsjos.content_review.review_timeout_reminder", "审核超时提醒", ROLE_DIRECTOR, ROLE_FINAL_REVIEWER)
        );
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();

        if (roles.contains(ROLE_DIRECTOR)) {
            addUsersFromPayload(recipients, payload, "directorUserIds");
        }
        if (roles.contains(ROLE_FINAL_REVIEWER)) {
            addUsersFromPayload(recipients, payload, "finalReviewerUserIds");
        }
        if (roles.contains(ROLE_OPERATOR)) {
            addUsersFromPayload(recipients, payload, "operatorUserIds");
        }
        if (roles.contains(ROLE_SUBMITTER)) {
            addUserFromPayload(recipients, payload, "submitterUserId");
        }

        return recipients;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("batch.id", event.getBizId());
        variables.put("event.time", event.getOccurredAt());
        if (event.getPayload() != null) {
            variables.putAll(event.getPayload());
        }
        return variables;
    }

    private NotifySceneRespDTO scene(String code, String name, String... roles) {
        return new NotifySceneRespDTO(code, name,
                List.of(
                        variable("batch.id", "批次ID"),
                        variable("batchNo", "批次编号"),
                        variable("batchTitle", "批次标题"),
                        variable("itemTitle", "内容标题"),
                        variable("itemCount", "内容数量"),
                        variable("decision", "审核结论"),
                        variable("comment", "审核意见"),
                        variable("reason", "驳回原因"),
                        variable("reviewerName", "审核人角色"),
                        variable("operatorName", "运营名称"),
                        variable("pendingHours", "待审时长(小时)"),
                        variable("deepLink", "详情链接"),
                        variable("event.time", "发生时间")),
                List.of(roles).stream().map(role -> new NotifySceneRoleRespDTO(role, roleName(role))).toList(),
                List.of(NotifyActionType.BUSINESS_DETAIL), "zsjos.content_review.review_timeout_reminder".equals(code));
    }

    private String roleName(String role) {
        return switch (role) {
            case ROLE_DIRECTOR -> "编导审核人";
            case ROLE_FINAL_REVIEWER -> "终审人";
            case ROLE_OPERATOR -> "运营";
            case ROLE_SUBMITTER -> "提交人";
            default -> role;
        };
    }

    private NotifySceneVariableRespDTO variable(String key, String name) {
        return new NotifySceneVariableRespDTO(key, name, false);
    }

    private void addUsersFromPayload(Set<NotifyRecipientDTO> recipients, Map<String, Object> payload, String key) {
        if (payload.get(key) instanceof Collection<?> userIds) {
            userIds.forEach(id -> addUser(recipients, id));
        }
    }

    private void addUserFromPayload(Set<NotifyRecipientDTO> recipients, Map<String, Object> payload, String key) {
        Object userId = payload.get(key);
        if (userId != null) {
            addUser(recipients, userId);
        }
    }

    private void addUser(Set<NotifyRecipientDTO> recipients, Object value) {
        if (value instanceof Number number && number.longValue() > 0) {
            recipients.add(NotifyRecipientDTO.admin(number.longValue()));
        } else if (value != null) {
            try {
                long id = Long.parseLong(String.valueOf(value));
                if (id > 0) recipients.add(NotifyRecipientDTO.admin(id));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
