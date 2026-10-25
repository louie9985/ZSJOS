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
    @org.junit.jupiter.api.BeforeEach
    void denyUnconfiguredFeaturePermissions() {
        org.mockito.Mockito.lenient().when(permissionApi.hasAnyPermissions(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(false);
    }


    @InjectMocks private StudentServiceObjectPermissionProvider provider;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private DeliveryClassMapper deliveryClassMapper;
    @Mock private DeliveryClassScopeService deliveryClassScopeService;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;

    @Test void queryAllReadsOnlyTeachingServicesAndDoesNotGrantCommandsOrTransferActions() {
        var relation = new ServiceRelationDO(); relation.setId(10L); relation.setOwnerUserId(99L);
        relation.setStatus("active"); relation.setTeachingDelivery(true);
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(7L, StudentReadScope.QUERY_ALL)).thenReturn(true);
        assertTrue(provider.hasPermission(10L, "read", 7L));
        for (String action : List.of("accept", "contact", "update-basic-info", "delivery-stage", "class-transfer", "close-service"))
            assertFalse(provider.hasPermission(10L, action, 7L));
        assertTrue(provider.transferActions(10L, 7L).isEmpty());
        relation.setTeachingDelivery(false);
        assertFalse(provider.hasPermission(10L, "read", 7L));
        relation.setTeachingDelivery(true); relation.setStatus("terminated");
        assertFalse(provider.hasPermission(10L, "read", 7L));
    }

    @Test void transferActionsRequireBothFeatureAndObjectPermission() {
        var relation = new ServiceRelationDO(); relation.setId(10L); relation.setOwnerUserId(7L);
        relation.setStatus("active"); relation.setTeachingDelivery(true);
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(7L, "zsjos:class-transfer:create")).thenReturn(true);
        assertTrue(provider.transferActions(10L, 7L).contains("REQUEST_TRANSFER"));
        relation.setOwnerUserId(99L);
        assertTrue(provider.transferActions(10L, 7L).isEmpty());
        relation.setOwnerUserId(7L); relation.setTeachingClosed(true);
        assertTrue(provider.transferActions(10L, 7L).isEmpty());
    }

    @Test void closedTeachingDeniesCommandsButPreservesMediaActions() {
        var relation=new ServiceRelationDO();relation.setId(10L);relation.setStatus("active");relation.setTeachingClosed(true);
        relation.setOwnerUserId(7L);relation.setContentDirectorUserId(8L);relation.setAcceptanceStatus("accepted");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        for (String action: List.of("accept","contact","delivery-stage","class-transfer","direct-transfer","update-basic-info","update-exam-date","archive-update"))
            assertFalse(provider.hasPermission(10L,action,7L));
        assertTrue(provider.hasPermission(10L,"close-service",7L));
        assertFalse(provider.hasPermission(10L,"close-service",9L));
        assertTrue(provider.hasPermission(10L,"director-precheck",8L));
    }
    @Test void restoreObjectScopeDoesNotExpandSelfIntoDepartmentAccess() {
        var relation=new ServiceRelationDO();relation.setId(10L);relation.setStatus("active");relation.setOwnerUserId(7L);
        var owner=new AdminUserRespDTO();owner.setId(7L);owner.setDeptId(80L);
        when(relationMapper.selectById(10L)).thenReturn(relation);when(adminUserApi.getUser(7L)).thenReturn(owner);
        when(deliveryClassScopeService.containsRead(9L,80L)).thenReturn(false);
        assertFalse(provider.hasPermission(10L,"restore-service",9L));
        when(deliveryClassScopeService.containsRead(9L,80L)).thenReturn(true);
        assertTrue(provider.hasPermission(10L,"restore-service",9L));
    }

    @Test
    void pendingServiceReadDoesNotGrantOwnerCommandsOrExposeOtherClasses() {
        org.mockito.Mockito.doReturn(false).when(permissionApi).hasAnyPermissions(7L, MediaStudentReadScope.QUERY_ALL);
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setClassId(90L); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(7L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(deliveryClassScopeService.resolveRead(7L)).thenReturn(new DeliveryClassScopeService.Scope(false, Set.of()));
        when(deliveryClassScopeService.readablePendingClassId(7L)).thenReturn(90L);
        assertTrue(provider.hasPermission(10L, "read", 7L));
        relation.setOwnerUserId(99L);
        assertTrue(provider.hasPermission(10L, "read", 7L));
        for (String action : List.of("accept", "contact", "update-basic-info", "delivery-stage")) {
            assertFalse(provider.hasPermission(10L, action, 7L));
        }
        relation.setClassId(91L); relation.setOwnerUserId(null);
        assertFalse(provider.hasPermission(10L, "read", 7L));
        relation.setClassId(null);
        assertFalse(provider.hasPermission(10L, "read", 7L));
        relation.setClassId(90L); relation.setStatus("terminated");
        assertFalse(provider.hasPermission(10L, "read", 7L));
    }

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
        source.setClassKind("UNSCHEDULED"); source.setHomeroomUserId(20L);
        assertFalse(provider.hasPermission(10L, "direct-transfer", 8L));
        assertTrue(provider.hasPermission(10L, "direct-transfer", 9L));
    }

    @Test
    void managedReadRequiresExactOwnerScopeAndDoesNotGrantCommands() {
        org.mockito.Mockito.doReturn(false).when(permissionApi).hasAnyPermissions(9L, MediaStudentReadScope.QUERY_ALL);
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(31L); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(deliveryClassScopeService.resolveRead(9L))
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
    void mediaAllReadsHistoricalInterviewButCannotSubmitIt() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setPersonId(20L); relation.setContentDirectorUserId(7L);
        relation.setStatus("completed"); relation.setAcceptanceStatus("accepted");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasTenantReadAllAccess(9L)).thenReturn(true);
        when(relationMapper.selectMediaReadByPersonIds(List.of(20L), null)).thenReturn(List.of(relation));
        assertTrue(provider.hasPermission(10L, "positioning-interview-read", 9L));
        assertFalse(provider.hasPermission(10L, "director-interview", 9L));
    }

    @Test
    void managedReadHonorsGlobalAndEmptyDepartmentScopes() {
        org.mockito.Mockito.doReturn(false).when(permissionApi).hasAnyPermissions(9L, MediaStudentReadScope.QUERY_ALL);
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(10L); relation.setOwnerUserId(31L); relation.setStatus("active");
        when(relationMapper.selectById(10L)).thenReturn(relation);
        when(permissionApi.hasAnyPermissions(9L, "zsjos:delivery-class:query", DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(deliveryClassScopeService.resolveRead(9L)).thenReturn(new DeliveryClassScopeService.Scope(true, Set.of()));
        assertTrue(provider.hasPermission(10L, "read", 9L));
        when(deliveryClassScopeService.resolveRead(9L)).thenReturn(new DeliveryClassScopeService.Scope(false, Set.of()));
        assertFalse(provider.hasPermission(10L, "read", 9L));
        relation.setOwnerUserId(9L);
        assertTrue(provider.hasPermission(10L, "read", 9L));
    }
    @Test void batchTransferActionsPreserveOwnerDepartmentClosedAndReadAllBoundaries() {
        var first=new ServiceRelationDO().setId(1L).setClassId(10L).setOwnerUserId(7L).setStatus("active").setTeachingDelivery(true);
        var foreign=new ServiceRelationDO().setId(2L).setClassId(20L).setOwnerUserId(99L).setStatus("active").setTeachingDelivery(true);
        var closed=new ServiceRelationDO().setId(3L).setClassId(10L).setOwnerUserId(7L).setStatus("active").setTeachingDelivery(true).setTeachingClosed(true);
        when(permissionApi.hasAnyPermissions(7L,"zsjos:delivery-class:direct-transfer")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(7L,"zsjos:class-transfer:create")).thenReturn(true);
        when(deliveryClassScopeService.resolve(7L)).thenReturn(new DeliveryClassScopeService.Scope(false,Set.of(80L)));
        when(deliveryClassMapper.selectByIds(List.of(10L,20L))).thenReturn(List.of(
                new DeliveryClassDO().setId(10L).setDeptId(80L).setClassKind("NORMAL"),
                new DeliveryClassDO().setId(20L).setDeptId(90L).setClassKind("NORMAL")));
        var result=provider.transferActions(List.of(first,foreign,closed),7L);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("DIRECT_TRANSFER","REQUEST_TRANSFER"),result.get(1L));
        assertTrue(result.get(2L).isEmpty());assertFalse(result.containsKey(3L));
        org.mockito.Mockito.verify(deliveryClassMapper,org.mockito.Mockito.times(1)).selectByIds(org.mockito.ArgumentMatchers.anyCollection());
        org.mockito.Mockito.verifyNoInteractions(relationMapper);
        org.mockito.Mockito.verify(permissionApi,org.mockito.Mockito.never()).hasTenantReadAllAccess(org.mockito.ArgumentMatchers.anyLong());
    }

}
