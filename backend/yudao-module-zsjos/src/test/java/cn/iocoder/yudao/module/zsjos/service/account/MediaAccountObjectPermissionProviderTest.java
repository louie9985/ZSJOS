package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaAccountObjectPermissionProviderTest {
    @InjectMocks private MediaAccountObjectPermissionProvider provider;
    @Mock private MediaAccountMapper mapper;
    @Mock private PermissionApi permissionApi;

    @Test
    void responsibleOperatorCanRescueButUnrelatedUserCannot() {
        when(mapper.selectById(1L)).thenReturn(new MediaAccountDO().setId(1L)
                .setOwnerOperatorUserId(230L).setDirectorUserId(248L));

        assertTrue(provider.hasPermission(1L, "rescue", 230L));
        assertTrue(provider.hasPermission(1L, "rescue", 248L));
        assertFalse(provider.hasPermission(1L, "rescue", 251L));
        assertTrue(provider.hasPermission(1L, "maintenance", 230L));
        assertTrue(provider.hasPermission(1L, "maintenance", 248L));
        assertFalse(provider.hasPermission(1L, "maintenance", 251L));
    }

    @Test
    void queryAllPermissionCanMaintainWithoutParticipantRelationship() {
        when(mapper.selectById(1L)).thenReturn(new MediaAccountDO().setId(1L)
                .setOwnerOperatorUserId(230L).setDirectorUserId(248L));
        when(permissionApi.hasAnyPermissions(251L, "zsjos:media-account:query-all")).thenReturn(true);

        assertTrue(provider.hasPermission(1L, "maintenance", 251L));
    }

    @Test
    void retiredStageTransitionActionsAreNeverAuthorized() {
        when(mapper.selectById(1L)).thenReturn(new MediaAccountDO().setId(1L)
                .setOwnerOperatorUserId(230L).setDirectorUserId(248L));

        assertFalse(provider.hasPermission(1L, "stage-advance", 230L));
        assertFalse(provider.hasPermission(1L, "stage-rollback", 248L));
    }
}
