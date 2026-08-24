package cn.iocoder.yudao.module.zsjos.service.media;

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
            Map.entry("media.ticket.pending_accept", "工单待接单"),
            Map.entry("media.ticket.pending_check", "工单待核对"),
            Map.entry("media.ticket.approved", "工单核对通过"),
            Map.entry("media.ticket.rejected", "工单返工"),
            Map.entry("media.content.pending_acceptance", "内容待验收"),
            Map.entry("media.content.approved", "内容验收通过"),
            Map.entry("media.content.rejected", "内容验收退回"),
            Map.entry("media.account.rebind_approved", "账号换绑通过"),
            Map.entry("media.account.rebind_rejected", "账号换绑驳回"),
            Map.entry("media.review.pending", "复盘待审核"),
            Map.entry("media.review.approved", "复盘审核通过"),
            Map.entry("media.review.rejected", "复盘审核退回"),
            Map.entry("media.graduation.result", "学员结业审批结果"),
            Map.entry("media.positioning.operator_review", "定位待运营复核"),
            Map.entry("media.positioning.operator_rejected", "定位运营退回"),
            Map.entry("media.positioning.ip_approved", "IP审核通过"),
            Map.entry("media.positioning.ip_rejected", "IP审核驳回"),
            Map.entry("media.positioning.student_confirmation", "定位待学员确认"),
            Map.entry("media.positioning.student_confirmed", "学员已确认定位"),
            Map.entry("media.positioning.student_rejected", "学员已拒绝定位"));

    @Override
    public List<NotifySceneRespDTO> getScenes() {
        return NAMES.entrySet().stream().map(entry -> new NotifySceneRespDTO(entry.getKey(), entry.getValue(),
                List.of(
                        variable("bizId", "业务对象内部ID"),
                        variable("bizNo", "业务编号"),
                        variable("deepLink", "业务深链"),
                        variable("reason", "处理原因"),
                        variable("event.time", "发生时间")),
                List.of(new NotifySceneRoleRespDTO("assignee", "业务责任人")),
                List.of(NotifyActionType.NONE, NotifyActionType.BUSINESS_DETAIL), false)).toList();
    }

    @Override
    public Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> roles) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Set<NotifyRecipientDTO> recipients = new LinkedHashSet<>();
        Object partnerAccountId = payload.get("partnerAccountId");
        if (partnerAccountId instanceof Number number && number.longValue() > 0) {
            recipients.add(NotifyRecipientDTO.partner(number.longValue()));
        }
        Object assigneeUserId = payload.get("assigneeUserId");
        if (assigneeUserId instanceof Number number && number.longValue() > 0) {
            recipients.add(NotifyRecipientDTO.admin(number.longValue()));
        }
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

    private NotifySceneVariableRespDTO variable(String key, String name) {
        return new NotifySceneVariableRespDTO(key, name, false);
    }
}
