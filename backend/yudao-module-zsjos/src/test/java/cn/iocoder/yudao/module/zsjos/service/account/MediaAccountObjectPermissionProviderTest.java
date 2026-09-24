package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
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
    @Test
    void batchReadMatchesSingleReadIncludingBrokenRelationsAndTenantBoundaries() {
        var relation = new ServiceRelationDO();
        relation.setId(30L); relation.setPersonId(40L); relation.setTenantId(1L);
        relation.setStatus("active"); relation.setAcceptanceStatus("accepted"); relation.setOperatorUserId(10L);
        var related = new MediaAccountDO().setId(1L).setStudentPersonId(40L).setCreateServiceRelationId(30L);
        related.setTenantId(1L);
        var crossTenant = new MediaAccountDO().setId(2L).setStudentPersonId(40L).setCreateServiceRelationId(30L);
        crossTenant.setTenantId(2L);
        var legacy = new MediaAccountDO().setId(3L).setOwnerOperatorUserId(10L);
        var denied = new MediaAccountDO().setId(4L).setOwnerOperatorUserId(20L);
        var accounts = java.util.List.of(related, crossTenant, legacy, denied);
        when(relationMapper.selectByIds(java.util.List.of(30L))).thenReturn(java.util.List.of(relation));
        when(relationMapper.selectById(30L)).thenReturn(relation);
        accounts.forEach(account -> when(mapper.selectById(account.getId())).thenReturn(account));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(related, legacy), provider.filterReadable(accounts, 10L));
        org.junit.jupiter.api.Assertions.assertEquals(accounts.stream().filter(a -> provider.hasPermission(a.getId(), "read", 10L)).toList(), provider.filterReadable(accounts, 10L));
        relation.setStatus("paused");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(legacy), provider.filterReadable(accounts, 10L));
        when(permissionApi.hasTenantReadAllAccess(10L)).thenReturn(true);
        org.junit.jupiter.api.Assertions.assertEquals(accounts.stream().filter(a -> provider.hasPermission(a.getId(), "read", 10L)).toList(), provider.filterReadable(accounts, 10L));
        when(permissionApi.hasTenantReadAllAccess(10L)).thenReturn(false);
        when(permissionApi.hasAnyPermissions(10L, "zsjos:media-account:query-all")).thenReturn(true);
        org.junit.jupiter.api.Assertions.assertEquals(accounts.stream().filter(a -> provider.hasPermission(a.getId(), "read", 10L)).toList(), provider.filterReadable(accounts, 10L));
    }

    @InjectMocks private MediaAccountObjectPermissionProvider provider;
    @Mock private MediaAccountMapper mapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ServiceRelationMapper relationMapper;

    @Test
    void tenantReadAllDoesNotGrantMaintenanceOrProduction() {
        when(mapper.selectById(1L)).thenReturn(new MediaAccountDO().setId(1L).setDirectorUserId(20L));
        when(permissionApi.hasTenantReadAllAccess(10L)).thenReturn(true);
        assertTrue(provider.hasPermission(1L, "read", 10L));
        assertFalse(provider.hasPermission(1L, "update", 10L));
        assertFalse(provider.hasPermission(1L, "maintenance", 10L));
        assertFalse(provider.hasPermission(1L, "production-ticket-create", 10L));
        assertFalse(provider.hasPermission(2L, "read", 10L));
    }

    @Test
    void sourcedAccountUsesOnlyExactActiveAcceptedRelationMembers() {
        MediaAccountDO account = new MediaAccountDO().setId(1L).setStudentPersonId(40L)
                .setCreateServiceRelationId(30L).setOwnerOperatorUserId(999L).setDirectorUserId(998L);
        account.setTenantId(1L);
        ServiceRelationDO relation = new ServiceRelationDO().setId(30L).setPersonId(40L)
                .setContentDirectorUserId(248L).setOperatorUserId(230L)
                .setStatus("active").setAcceptanceStatus("accepted");
        relation.setTenantId(1L);
        when(mapper.selectById(1L)).thenReturn(account);
        when(relationMapper.selectById(30L)).thenReturn(relation);
        assertTrue(provider.hasPermission(1L, "read", 248L));
        assertTrue(provider.hasPermission(1L, "update", 230L));
        assertTrue(provider.hasPermission(1L, "production-ticket-create", 230L));
        assertFalse(provider.hasPermission(1L, "production-ticket-create", 248L));
        assertFalse(provider.hasPermission(1L, "read", 999L));
        assertFalse(provider.hasPermission(1L, "update", 998L));
        assertFalse(provider.hasPermission(1L, "read", 777L));
        relation.setPersonId(41L);
        assertFalse(provider.hasPermission(1L, "read", 248L));
        relation.setPersonId(40L); relation.setTenantId(2L);
        assertFalse(provider.hasPermission(1L, "read", 248L));
        relation.setTenantId(1L); relation.setAcceptanceStatus("pending");
        assertFalse(provider.hasPermission(1L, "update", 230L));
        relation.setAcceptanceStatus("accepted"); relation.setStatus("paused");
        assertFalse(provider.hasPermission(1L, "read", 248L));
        when(relationMapper.selectById(30L)).thenReturn(null);
        assertFalse(provider.hasPermission(1L, "read", 998L));
    }

    @Test
    void queryAllCannotGrantGenericWritesEvenWhenRelationMissing() {
        when(mapper.selectById(1L)).thenReturn(new MediaAccountDO().setId(1L).setCreateServiceRelationId(30L));
        when(permissionApi.hasAnyPermissions(251L, "zsjos:media-account:query-all")).thenReturn(true);
        assertTrue(provider.hasPermission(1L, "read", 251L));
        assertTrue(provider.hasPermission(1L, "maintenance", 251L));
        assertFalse(provider.hasPermission(1L, "update", 251L));
        assertFalse(provider.hasPermission(1L, "production-ticket-create", 251L));
    }

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
