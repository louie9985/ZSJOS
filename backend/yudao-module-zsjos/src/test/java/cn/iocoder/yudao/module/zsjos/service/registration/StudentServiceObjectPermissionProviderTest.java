package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassScopeService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceObjectPermissionProviderTest {

    @InjectMocks private StudentServiceObjectPermissionProvider provider;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private DeliveryClassMapper deliveryClassMapper;
    @Mock private DeliveryClassScopeService deliveryClassScopeService;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;

    @Test
    void deliveryStageRequiresActiveOwner() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(7L); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);

        assertTrue(provider.hasPermission(10L, "delivery-stage", 7L));
        assertFalse(provider.hasPermission(10L, "delivery-stage", 8L));

        relation.setStatus("completed");
        assertFalse(provider.hasPermission(10L, "delivery-stage", 7L));
    }

    @Test
    void serviceOwnerWhoIsAlsoDirectorRetainsDirectorActions() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(7L); relation.setContentDirectorUserId(7L);
        relation.setStatus("active"); relation.setAcceptanceStatus("accepted");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        assertTrue(provider.hasPermission(10L,"director-precheck",7L));
        assertTrue(provider.hasPermission(10L,"director-interview",7L));
        relation.setContentDirectorUserId(8L);
        assertFalse(provider.hasPermission(10L,"director-interview",7L));
    }

    @Test
    void mediaAccountCreationRequiresCurrentAcceptedDirectorOrOperator() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(6L); relation.setContentDirectorUserId(7L);
        relation.setOperatorUserId(8L); relation.setStatus("active"); relation.setAcceptanceStatus("accepted");
        when(relationMapper.selectById(10L)).thenReturn(relation);

        assertTrue(provider.hasPermission(10L, "create-account", 7L));
        assertFalse(provider.hasPermission(10L, "create-account", 6L));
        assertTrue(provider.hasPermission(10L, "create-account", 8L));

        relation.setAcceptanceStatus("pending");
        assertFalse(provider.hasPermission(10L, "create-account", 7L));
        assertFalse(provider.hasPermission(10L, "create-account", 8L));
    }

    @Test
    void assignedOperatorCanOnlyReadAcceptedServiceAcrossReadableStatuses() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(7L); relation.setOperatorUserId(9L);
        relation.setAcceptanceStatus("accepted"); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);

        for (String status : new String[]{"active", "paused", "completed"}) {
            relation.setStatus(status);
            assertTrue(provider.hasPermission(10L, "read", 9L));
            assertFalse(provider.hasPermission(10L, "contact", 9L));
        }

        relation.setStatus("active"); relation.setAcceptanceStatus("pending");
        assertFalse(provider.hasPermission(10L, "read", 9L));
        relation.setAcceptanceStatus("accepted"); relation.setOperatorUserId(10L);
        assertFalse(provider.hasPermission(10L, "read", 9L));
    }

    @Test
    void plannerCanRequestTransferForOwnedReadableService() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(7L); relation.setStatus("completed");
        when(relationMapper.selectById(10L)).thenReturn(relation);

        assertTrue(provider.hasPermission(10L, "class-transfer", 7L));
        assertFalse(provider.hasPermission(10L, "class-transfer", 8L));
    }

    @Test
    void managerDirectTransferRequiresSourceClassScopeUnlessPending() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setClassId(100L); relation.setStatus("active");
        DeliveryClassDO source = new DeliveryClassDO();
        source.setId(100L); source.setSystemClass(false); source.setDeptId(80L);
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(deliveryClassMapper.selectById(100L)).thenReturn(source);
        when(deliveryClassScopeService.contains(9L, 80L)).thenReturn(true);

        assertTrue(provider.hasPermission(10L, "direct-transfer", 9L));
        assertFalse(provider.hasPermission(10L, "direct-transfer", 8L));

        source.setSystemClass(true);
        assertTrue(provider.hasPermission(10L, "direct-transfer", 8L));
    }

    @Test
    void managedReadRequiresExactOwnerScopeAndDoesNotGrantCommands() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(31L); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(deliveryClassScopeService.resolve(9L))
                .thenReturn(new DeliveryClassScopeService.Scope(false, Set.of(80L)));
        when(adminUserApi.getUserListByDeptIds(Set.of(80L)))
                .thenReturn(List.of(new AdminUserRespDTO().setId(31L)));
        for (String status : List.of("active", "paused", "completed")) {
            relation.setStatus(status);
            assertTrue(provider.hasPermission(10L, "read", 9L));
            assertFalse(provider.hasPermission(10L, "contact", 9L));
            assertFalse(provider.hasPermission(10L, "accept", 9L));
        }
        relation.setOwnerUserId(99L);
        assertFalse(provider.hasPermission(10L, "read", 9L));
        relation.setOwnerUserId(31L); relation.setStatus("closed");
        assertFalse(provider.hasPermission(10L, "read", 9L));
    }

    @Test
    void managedReadHonorsGlobalAndEmptyDepartmentScopes() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(31L); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(deliveryClassScopeService.resolve(9L)).thenReturn(new DeliveryClassScopeService.Scope(true, Set.of()));
        assertTrue(provider.hasPermission(10L, "read", 9L));
        when(deliveryClassScopeService.resolve(9L)).thenReturn(new DeliveryClassScopeService.Scope(false, Set.of()));
        assertFalse(provider.hasPermission(10L, "read", 9L));
        relation.setOwnerUserId(9L);
        assertTrue(provider.hasPermission(10L, "read", 9L));
    }
}
