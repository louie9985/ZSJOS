package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationNotifySceneProviderTest {

    @InjectMocks private RegistrationNotifySceneProvider provider;
    @Mock private PermissionApi permissionApi;

    @Test
    void resolvesEnabledPoolHandlersFromPermissionApi() {
        when(permissionApi.getEnabledUserIdsByPermission(PERMISSION_QUERY_POOL)).thenReturn(Set.of(11L, 12L));
        NotifyBusinessEvent event = NotifyBusinessEvent.builder().tenantId(1L).bizId(7L).payload(Map.of()).build();

        Set<NotifyRecipientDTO> recipients = provider.resolveRecipients(event, Set.of(NOTIFY_ROLE_POOL_HANDLERS));

        assertEquals(Set.of(NotifyRecipientDTO.admin(11L), NotifyRecipientDTO.admin(12L)), recipients);
    }
}
