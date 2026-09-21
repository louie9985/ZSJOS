package cn.iocoder.yudao.module.zsjos.service.media;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Freezes real collaborators at the business transition; never resolves recipients from role names. */
@Service
public class MediaCollaborationNotifyPublisher {
    @Resource private MediaWorkflowEventService events;

    public void account(String scene, MediaAccountDO account, Long actor, String key, Map<String,Object> extra) {
        if (account == null) return;
        send(scene, "media-account", account.getId(), account.getAccountNo(), actor, key,
                Arrays.asList(account.getDirectorUserId(), account.getOwnerOperatorUserId()), extra);
    }
    public void card(String scene, PositioningCardDO card, Long actor, String key) {
        send(scene, "positioning-card", card.getId(), card.getCardNo(), actor, key,
                Arrays.asList(card.getDirectorUserId(), card.getOperatorUserId()), Map.of());
    }
    public void student(String scene, ServiceRelationDO relation, Long actor, String key) {
        Map<String,Object> extra = new LinkedHashMap<>();
        extra.put("interviewAt", relation.getDirectorInterviewAt());
        send(scene, "student_service", relation.getId(), "", actor, key,
                Arrays.asList(relation.getOwnerUserId(), relation.getContentDirectorUserId(), relation.getOperatorUserId()), extra);
    }
    public void send(String scene, String type, Long id, String number, Long actor, String key,
                     Collection<Long> recipients, Map<String,Object> extra) {
        Set<Long> ids = new LinkedHashSet<>(recipients);
        ids.removeIf(user -> user == null || user <= 0 || Objects.equals(user, actor));
        if (ids.isEmpty()) return;
        Map<String,Object> payload = new LinkedHashMap<>(extra);
        payload.put("bizNo", number == null ? "" : number);
        payload.put("assigneeUserIds", ids);
        events.notify(scene, type, id, null, actor, key, payload);
    }
}
