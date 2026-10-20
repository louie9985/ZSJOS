package cn.iocoder.yudao.module.zsjos.service.educationteam;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.educationteam.EducationTeamOrganizationMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class EducationTeamScopeTest {
 @InjectMocks EducationTeamScope scope;
 @Mock EducationTeamOrganizationMapper organizations;@Mock AdminUserApi users;@Mock PermissionApi permissions;@Mock LeadObjectPermissionService managers;@Mock LeadTeamTransferPolicy ownership;
 AdminUserRespDTO user(long id,long dept,int status){var u=new AdminUserRespDTO();u.setId(id);u.setDeptId(dept);u.setStatus(status);return u;}
 @Test void navigationUsesCurrentConfiguredDepartmentWithoutRoleOrPermissionInference(){
  when(users.getUser(1L)).thenReturn(user(1,10,0));when(organizations.deliveryDepartments()).thenReturn(List.of(10L));assertTrue(scope.deliveryNavigation(1L));
  when(users.getUser(1L)).thenReturn(user(1,20,0));assertFalse(scope.deliveryNavigation(1L));
  when(users.getUser(1L)).thenReturn(null);assertFalse(scope.deliveryNavigation(1L));verifyNoInteractions(permissions,managers);
 }
 @Test void managerScopeIntersectsConfiguredDeliveryDepartmentsAndKeepsDisabledMembers(){
  when(managers.getManagedUserIds(1L)).thenReturn(Set.of(2L,3L,4L));when(users.getUserList(Set.of(2L,3L,4L))).thenReturn(List.of(user(2,10,0),user(3,20,0),user(4,10,1)));when(organizations.deliveryDepartments()).thenReturn(List.of(10L));
  assertEquals(Set.of(2L,4L),scope.managedIds(1L));assertThrows(RuntimeException.class,()->scope.requireManaged(3L,1L));
 }
 @Test void tenantReadAllNeverGrantsCommandsOrSalesDepartmentAccess(){
  when(permissions.hasTenantReadAllAccess(1L)).thenReturn(true);when(users.getUserListByStatus(0)).thenReturn(List.of(user(2,10,0),user(3,20,0)));when(users.getUserListByStatus(1)).thenReturn(List.of(user(4,10,1)));when(organizations.deliveryDepartments()).thenReturn(List.of(10L));
  assertEquals(List.of(2L,4L),scope.readable(1L).stream().map(AdminUserRespDTO::getId).toList());assertThrows(RuntimeException.class,()->scope.requireManaged(2L,1L));
 }
 @Test void emptyConfigurationFailsClosed(){when(managers.getManagedUserIds(1L)).thenReturn(Set.of(2L));when(users.getUserList(Set.of(2L))).thenReturn(List.of(user(2,10,0)));assertTrue(scope.readable(1L).isEmpty());}
 @Test void salesIdentityCannotEnterEducationCommandBoundary(){var lead=new LeadDO();assertThrows(RuntimeException.class,()->scope.requireEducationLead(lead));when(ownership.isEducation(lead)).thenReturn(true);assertDoesNotThrow(()->scope.requireEducationLead(lead));}
}
