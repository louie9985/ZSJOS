package cn.iocoder.yudao.module.zsjos.service.lead;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class LeadMetricVisibilityScopeTest {
 @InjectMocks LeadManagementServiceImpl service;
 @Mock SecurityFrameworkService securityFrameworkService;
 @Mock LeadObjectPermissionService leadObjectPermissionService;
 @Test void scopePermissionCannotReplaceBaseLeadPagePermission(){
  when(securityFrameworkService.hasPermission(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
   case "zsjos:lead:query-owned" -> true;
   case "zsjos:lead:query", "zsjos:lead:query-submitted", "zsjos:lead:query-all" -> false;
   default -> throw new AssertionError("Unexpected permission: " + call.getArgument(0));
  });
  assertThrows(ServiceException.class,()->service.metricVisibilityScope(7L));
  verifyNoInteractions(leadObjectPermissionService);
 }
 @Test void basePageDoesNotInventDataRange(){
  doReturn(true).when(securityFrameworkService).hasPermission("zsjos:lead:query");
  doReturn(false).when(securityFrameworkService).hasPermission("zsjos:lead:query-submitted");
  doReturn(false).when(securityFrameworkService).hasPermission("zsjos:lead:query-owned");
  doReturn(false).when(securityFrameworkService).hasPermission("zsjos:lead:query-all");
  when(leadObjectPermissionService.hasTenantReadAll(7L)).thenReturn(false);
  assertThrows(ServiceException.class,()->service.metricVisibilityScope(7L));
 }
 @Test void allowedOwnedViewReusesExistingManagedUserRange(){
  doReturn(true).when(securityFrameworkService).hasPermission("zsjos:lead:query");
  doReturn(false).when(securityFrameworkService).hasPermission("zsjos:lead:query-submitted");
  doReturn(true).when(securityFrameworkService).hasPermission("zsjos:lead:query-owned");
  when(leadObjectPermissionService.hasTenantReadAll(7L)).thenReturn(false);
  when(leadObjectPermissionService.hasQueryAll()).thenReturn(false);
  when(leadObjectPermissionService.getRelatedAndManagedUserIds(7L)).thenReturn(Set.of(7L,8L));
  var scope=service.metricVisibilityScope(7L);
  assertFalse(scope.queryAll());assertTrue(scope.sourceUserIds().isEmpty());assertEquals(java.util.List.of(7L,8L),scope.ownerUserIds());
 }
}
