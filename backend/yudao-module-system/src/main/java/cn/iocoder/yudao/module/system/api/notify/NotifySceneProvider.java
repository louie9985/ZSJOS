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

    Set<NotifyRecipientDTO> resolveRecipients(NotifyBusinessEvent event, Set<String> recipientRoles);

    Map<String, Object> resolveVariables(NotifyBusinessEvent event, NotifyRecipientDTO recipient);
}
