package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceAnnotationConstants.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceAnnotationPermissionProviderTest {
    @InjectMocks StudentServiceAnnotationPermissionProvider provider;
    @Mock ServiceRelationMapper relations;
    @Mock StudentServiceObjectPermissionProvider servicePermissions;
    @Mock PermissionApi permissions;
    @Mock AdminUserApi users;
    @Mock DeliveryClassScopeService scope;
    ServiceRelationDO row;
    @BeforeEach void setup() {
        row=new ServiceRelationDO();row.setId(11L);row.setStatus("active");row.setTeachingDelivery(true);row.setOwnerUserId(7L);
        lenient().when(relations.selectById(11L)).thenReturn(row);
        lenient().when(servicePermissions.hasPermission(11L,"read",7L)).thenReturn(true);
        lenient().when(permissions.hasAnyPermissions(7L,READ)).thenReturn(true);
        lenient().when(permissions.hasAnyPermissions(7L,UPDATE)).thenReturn(true);
    }
    @Test void ownerNeedsConfiguredFeaturePermission() {
        assertTrue(provider.hasPermission(11L,"update",7L));
        when(permissions.hasAnyPermissions(7L,UPDATE)).thenReturn(false);
        assertFalse(provider.hasPermission(11L,"update",7L));assertTrue(provider.hasPermission(11L,"read",7L));
    }
    @Test void managerNeedsExactOwnerDepartmentScope() {
        row.setOwnerUserId(8L);var owner=new AdminUserRespDTO();owner.setDeptId(20L);
        when(permissions.hasAnyPermissions(7L,DeliveryClassService.PERMISSION_QUERY_MANAGED)).thenReturn(true);
        when(users.getUser(8L)).thenReturn(owner);when(scope.containsRead(7L,20L)).thenReturn(true);
        assertTrue(provider.hasPermission(11L,"update",7L));
        when(scope.containsRead(7L,20L)).thenReturn(false);assertFalse(provider.hasPermission(11L,"update",7L));
    }
    @Test void collaboratorAndReadAllDoNotGrantWrites() {
        row.setOwnerUserId(8L);row.setContentDirectorUserId(7L);assertTrue(provider.hasPermission(11L,"read",7L));assertFalse(provider.hasPermission(11L,"update",7L));
    }
    @Test void closedServicesAreReadOnly() {
        row.setTeachingClosed(true);assertTrue(provider.hasPermission(11L,"read",7L));assertFalse(provider.hasPermission(11L,"update",7L));
    }
    @Test void nonTeachingOrInvisibleOrMissingRelationsAreDenied() {
        row.setTeachingDelivery(false);assertFalse(provider.hasPermission(11L,"read",7L));
        row.setTeachingDelivery(true);when(servicePermissions.hasPermission(11L,"read",7L)).thenReturn(false);assertFalse(provider.hasPermission(11L,"update",7L));
        assertFalse(provider.hasPermission(12L,"read",7L));assertFalse(provider.hasPermission(11L,"read",null));
    }
    @Test void unknownCommandsFailClosedAndReadPermissionIsRequiredForEditing() {
        assertFalse(provider.hasPermission(11L,"delete",7L));when(permissions.hasAnyPermissions(7L,READ)).thenReturn(false);assertFalse(provider.hasPermission(11L,"update",7L));
    }
}
