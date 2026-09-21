package cn.iocoder.yudao.module.zsjos.service.media;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;

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
public class MediaNotifySceneProvider implements NotifySceneProvider {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry(MEDIA_STUDENT_INTERVIEW_SCHEDULED, "定位访谈已预约"),
            Map.entry(MEDIA_STUDENT_INTERVIEW_REMINDER, "定位访谈时间提醒"),
            Map.entry(MEDIA_STUDENT_INTERVIEW_COMPLETED, "定位访谈已完成"),
            Map.entry(MEDIA_STUDENT_COLLABORATOR_CHANGED, "学员协作责任已移交"),
            Map.entry(MEDIA_POSITIONING_OPERATOR_APPROVED, "定位运营复核通过"),
            Map.entry(MEDIA_POSITIONING_CONFIRMATION_READY, "学员确认链接已生成"),
            Map.entry(MEDIA_POSITIONING_EFFECTIVE, "定位确认凭证已完成"),
            Map.entry(MEDIA_POSITIONING_APPLIED, "账号应用定位已更新"),
            Map.entry(MEDIA_POSITIONING_REVISION_STARTED, "定位已开始修订"),
            Map.entry(MEDIA_ACCOUNT_CREATED, "媒体账号已创建"),
            Map.entry(MEDIA_ACCOUNT_DIAGNOSIS_COMPLETED, "账号诊断已提交"),
            Map.entry(STUDENT_DELIVERY_COMPLETED, "阶段交付已完成"),
            Map.entry(MEDIA_CONTENT_PUBLISHED, "内容已登记发布"),
            Map.entry(MEDIA_TICKET_PENDING_ACCEPT, "工单待接单"),
            Map.entry(MEDIA_TICKET_PENDING_CHECK, "工单待核对"),
            Map.entry(MEDIA_TICKET_APPROVED, "工单核对通过"),
            Map.entry(MEDIA_TICKET_REJECTED, "工单返工"),
            Map.entry(MEDIA_TICKET_ASSIGNMENT_REJECTED, "指定拍剪工单被拒接"),
            Map.entry(MEDIA_TICKET_CLAIMED, "公共池拍剪工单已被抢单"),
            Map.entry(MEDIA_CONTENT_PENDING_ACCEPTANCE, "内容待验收"),
            Map.entry(MEDIA_CONTENT_APPROVED, "内容验收通过"),
            Map.entry(MEDIA_CONTENT_REJECTED, "内容验收退回"),
            Map.entry(MEDIA_ACCOUNT_REBIND_APPROVED, "账号换绑通过"),
            Map.entry(MEDIA_ACCOUNT_REBIND_REJECTED, "账号换绑驳回"),
            Map.entry(MEDIA_ACCOUNT_MAINTENANCE_CHANGED, "账号状态维护变更"),
            Map.entry(MEDIA_ACCOUNT_DIAGNOSIS, "账号周期诊断逾期提醒"),
            Map.entry(STUDENT_DELIVERY_DEFERRED, "账号交付已延期"),
            Map.entry(STUDENT_DELIVERY_CONFIRMATION, "S0-S6交付确认待填写"),
            Map.entry(MEDIA_POSITIONING_OPERATOR_REVIEW, "定位待运营复核"),
            Map.entry(MEDIA_POSITIONING_OPERATOR_REJECTED, "定位运营退回"),
            Map.entry(MEDIA_POSITIONING_IP_APPROVED, "IP审核通过"),
            Map.entry(MEDIA_POSITIONING_IP_REJECTED, "IP审核驳回"),
            Map.entry(MEDIA_POSITIONING_STUDENT_CONFIRMATION, "定位待学员确认"),
            Map.entry(MEDIA_POSITIONING_STUDENT_CONFIRMED, "学员已确认定位"),
            Map.entry(MEDIA_POSITIONING_STUDENT_REJECTED, "学员已拒绝定位"));

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return NAMES.entrySet().stream().map(entry -> new NotifySceneRespDTO(entry.getKey(), entry.getValue(),
                List.of(
                        variable("stageCode", "交付阶段"),
                        variable("interviewAt", "定位访谈时间"),
                        variable("cycle", "诊断周期"),
                        variable("bizId", "业务对象内部ID"),
                        variable("bizNo", "业务编号"),
                        variable("deepLink", "业务深链"),
                        variable("reason", "处理原因"),
                        variable("accountName", "账号名称"),
                        variable("operatorName", "操作人"),
                        variable("changedFields", "变更字段"),
                        variable("changeSummary", "变更摘要"),
                        variable("event.time", "发生时间")),
                List.of(new NotifySceneRoleRespDTO("assignee", "业务责任人"), new NotifySceneRoleRespDTO("supervisor", "直属上级")),
                List.of(NotifyActionType.NONE, NotifyActionType.BUSINESS_DETAIL), Set.of(MEDIA_ACCOUNT_DIAGNOSIS, STUDENT_DELIVERY_CONFIRMATION, MEDIA_STUDENT_INTERVIEW_REMINDER).contains(entry.getKey()))).toList();
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();
        if (roles.contains("assignee")) {
            Object partnerAccountId = payload.get("partnerAccountId");
            if (partnerAccountId instanceof Number number && number.longValue() > 0)
                recipients.add(NotifyRecipientDTO.partner(number.longValue()));
            addAdmin(recipients, payload.get("assigneeUserId"));
            if (payload.get("assigneeUserIds") instanceof java.util.Collection<?> ids)
                ids.forEach(id -> addAdmin(recipients, id));
        }
        if (roles.contains("supervisor")) addAdmin(recipients, payload.get("supervisorUserId"));
        return recipients;
    }

    @Override
    public Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("bizId", event.getBizId());
        values.put("event.time", event.getOccurredAt());
        if (event.getPayload() != null) values.putAll(event.getPayload());
        return values;
    }

    private void addAdmin(Set<NotifyRecipientDTO> recipients, Object value) {
        if (value instanceof Number number && number.longValue() > 0)
            recipients.add(NotifyRecipientDTO.admin(number.longValue()));
    }

    private NotifySceneVariableRespDTO variable(String key, String name) {
        return new NotifySceneVariableRespDTO(key, name, false);
    }
}
