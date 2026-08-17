package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BpmNotifySceneProviderTest {

    @Test
    void resolvesTypedPartnerTarget() {
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().sceneCode("bpm.process.approved")
                .payload(Map.of("targetUserType", UserTypeEnum.PARTNER.getValue(), "targetUserId", 20L)).build();

        Set<NotifyRecipientDTO> recipients = new BpmNotifySceneProvider()
                .resolveRecipients(event, Set.of("target_user"));

        assertEquals(Set.of(new NotifyRecipientDTO(UserTypeEnum.PARTNER.getValue(), 20L)), recipients);
    }
}
