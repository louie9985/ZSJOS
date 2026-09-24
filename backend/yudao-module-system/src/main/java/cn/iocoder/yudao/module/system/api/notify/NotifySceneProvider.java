package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Extension point implemented by the module that owns a business notification scene. */
public interface NotifySceneProvider {

    List<NotifySceneRespDTO> getScenes();

    /** Null permits delivery; a stable reason code skips obsolete business notifications without retry. */
    default String deliverySkipReason(NotifyBusinessEvent event) { return null; }

    /** Null continues normal delivery. A terminal skip or failure concerns this rule, not the entire scene. */
    default cn.iocoder.yudao.module.system.api.notify.dto.NotifySendResult evaluateRule(
            NotifyBusinessEvent event, Set<String> recipientRoles, Set<Long> specifiedUserIds) { return null; }

    Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> recipientRoles);

    Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient);
}
