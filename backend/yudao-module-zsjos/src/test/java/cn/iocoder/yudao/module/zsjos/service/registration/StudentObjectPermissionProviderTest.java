package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentObjectPermissionProviderTest {
    @org.junit.jupiter.api.BeforeEach
    void denyUnconfiguredFeaturePermissions() {
        org.mockito.Mockito.lenient().when(permissionApi.hasAnyPermissions(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(false);
    }

    @InjectMocks StudentObjectPermissionProvider provider;
    @Mock ServiceRelationMapper relationMapper;
    @Mock PermissionApi permissionApi;
    @Mock DeliveryClassScopeService scopeService;
    @Mock AdminUserApi adminUserApi;

    @Test void queryAllLegacyPersonReadRequiresATeachingServiceAndGrantsNoCommand() {
        when(permissionApi.hasAnyPermissions(7L, StudentReadScope.QUERY_ALL)).thenReturn(true);
        var relation = new ServiceRelationDO(); relation.setTeachingDelivery(true);
        when(relationMapper.selectOwnedByPersonIds(List.of(9L), null)).thenReturn(List.of(relation));
        assertTrue(provider.hasPermission(9L, "read", 7L));
        assertFalse(provider.hasPermission(9L, "contact", 7L));
        relation.setTeachingDelivery(false);
        assertFalse(provider.hasPermission(9L, "read", 7L));
        assertFalse(provider.hasPermission(10L, "read", 7L));
    }

    @Test void teachingQueryAllDoesNotRemoveExistingMediaCollaborationRead() {
        when(permissionApi.hasAnyPermissions(7L, StudentReadScope.QUERY_ALL)).thenReturn(true);
        var relation = new ServiceRelationDO(); relation.setTeachingDelivery(false);
        when(relationMapper.selectOwnedByPersonIds(List.of(9L), null)).thenReturn(List.of(relation));
        when(relationMapper.existsActiveByCollaboratorAndPerson(7L, 9L)).thenReturn(true);
        assertTrue(provider.hasPermission(9L, "read", 7L));
    }

    @Test void legacyPersonReadUsesPendingScopeButDoesNotGrantCommands() {
        when(permissionApi.hasAnyPermissions(7L,"zsjos:delivery-class:query",DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(scopeService.resolveRead(7L)).thenReturn(new DeliveryClassScopeService.Scope(false,Set.of()));
        when(scopeService.readablePendingClassId(7L)).thenReturn(90L);
        when(relationMapper.selectManagedByPersonIds(Set.of(7L),List.of(9L),null,90L)).thenReturn(List.of(new ServiceRelationDO()));
        assertTrue(provider.hasPermission(9L,"read",7L));
        assertFalse(provider.hasPermission(9L,"contact",7L));
        assertFalse(provider.hasPermission(10L,"read",7L));
    }
    @Test void ordinaryReaderHasNoPendingScope() {
        assertFalse(provider.hasPermission(9L,"read",7L));
        verifyNoInteractions(scopeService,adminUserApi);
    }
}
