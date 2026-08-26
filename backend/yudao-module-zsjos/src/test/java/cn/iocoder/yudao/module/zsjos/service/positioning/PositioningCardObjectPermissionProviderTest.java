package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositioningCardObjectPermissionProviderTest {
    @Mock private PositioningCardMapper mapper;
    @Mock private PermissionApi permissionApi;
    @Mock private MediaAccountMapper accountMapper;
    @InjectMocks private PositioningCardObjectPermissionProvider provider;

    @Test
    void boundCardUsesFrozenDirectorAndOperatorOnly() {
        PositioningCardDO card = new PositioningCardDO().setId(1L).setAccountId(2L).setServiceRelationId(3L)
                .setDirectorUserId(10L).setOperatorUserId(20L);
        when(mapper.selectById(1L)).thenReturn(card);

        assertTrue(provider.hasPermission(1L, "read", 10L));
        assertTrue(provider.hasPermission(1L, "read", 20L));
        assertFalse(provider.hasPermission(1L, "read", 30L));
    }

    @Test
    void legacyCardFallsBackToAccountOwner() {
        PositioningCardDO card = new PositioningCardDO().setId(1L).setAccountId(2L).setDirectorUserId(10L);
        when(mapper.selectById(1L)).thenReturn(card);
        when(accountMapper.selectById(2L)).thenReturn(new MediaAccountDO().setId(2L).setOwnerOperatorUserId(20L));

        assertTrue(provider.hasPermission(1L, "read", 20L));
        assertFalse(provider.hasPermission(1L, "read", 30L));
    }
}
