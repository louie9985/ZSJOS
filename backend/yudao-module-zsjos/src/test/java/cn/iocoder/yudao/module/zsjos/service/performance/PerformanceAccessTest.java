package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceOrgMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceOrgDO;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.Query;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class) class PerformanceAccessTest {
 @InjectMocks PerformanceAccess access;
 @Mock PermissionApi permissionApi; @Mock AdminUserApi userApi; @Mock PerformanceOrgMapper orgMapper;
 @Mock cn.iocoder.yudao.module.system.api.dept.PostApi postApi;
 @Mock cn.iocoder.yudao.module.system.api.dept.DeptApi deptApi;
 MockedStatic<SecurityFrameworkUtils> security;
 @BeforeEach void setup(){security=mockStatic(SecurityFrameworkUtils.class);security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);}
 @AfterEach void close(){security.close();}
 Query q(String type,long id){var q=new Query();q.setScopeType(type);q.setScopeId(id);return q;}
 AdminUserRespDTO person(long id,Integer status){var u=new AdminUserRespDTO();u.setId(id);u.setStatus(status);u.setDeptId(10L);return u;}
 @Test void bothTreesExcludeDisabledAndUnknownStatusUsers(){
  var post=new cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO();post.setId(7L);
  when(postApi.getPostByCode("sales_specialist")).thenReturn(post);
  var enabled=person(2,0);
  when(userApi.getUserListByPostIds(List.of(7L))).thenReturn(List.of(enabled,person(3,1),person(4,null)));
  when(userApi.getUser(2L)).thenReturn(enabled);
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance-target:query")).thenReturn(true);
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:department")).thenReturn(true);
  assertEquals(List.of(2L),access.tree(true).stream().map(n->n.scopeId()).toList());
  assertEquals(List.of(2L),access.tree(false).stream().map(n->n.scopeId()).toList());
  verify(userApi,never()).getUser(3L);verify(userApi,never()).getUser(4L);
  assertEquals(List.of(2L),access.sales().stream().map(AdminUserRespDTO::getId).toList());
 }
 @Test void enabledHistoricalContributorsNeedNotStillHoldSalesPost(){
  when(userApi.getUserList(Set.of(2L,3L,4L))).thenReturn(List.of(person(2,0),person(3,1)));
  assertEquals(Set.of(2L),access.enabledUserIds(Set.of(2L,3L,4L)));
  assertTrue(access.enabledUserIds(Set.of()).isEmpty());verifyNoInteractions(postApi);
 }
 @Test void disabledTargetCannotBeSavedDespiteReadAndWritePermissions(){
  when(userApi.getUser(2L)).thenReturn(person(2,1));
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance-target:query")).thenReturn(true);
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  var ex=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->access.targetWriteObject("USER",2L));
  assertTrue(ex.getMessage().contains("停用"));
 }
 PerformanceOrgDO org(long id,long center,String kind){var o=new PerformanceOrgDO();o.setDeptId(id);o.setCenterId(center);o.setKind(kind);return o;}
 @Test void selfCannotSpoofUser(){when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:self")).thenReturn(true);assertEquals(1L,access.authorize(q("SELF",999),false).getScopeId());}
 @Test void selfNeedsViewPermission(){assertThrows(RuntimeException.class,()->access.authorize(q("SELF",1),false));}
 @Test void featurePermissionDoesNotBypassDataScope(){when(orgMapper.selectList()).thenReturn(List.of(org(10,9,"DEPT")));when(permissionApi.getDeptDataPermission(1L)).thenReturn(new DeptDataPermissionRespDTO());assertThrows(RuntimeException.class,()->access.authorize(q("DEPT",10),false));}
 @Test void scopedDepartmentAllowed(){when(orgMapper.selectList()).thenReturn(List.of(org(10,9,"DEPT")));var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(10L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:department")).thenReturn(true);assertDoesNotThrow(()->access.authorize(q("DEPT",10),false));}
 @Test void tenantReadAllStillNeedsFeature(){when(orgMapper.selectList()).thenReturn(List.of(org(10,9,"DEPT")));when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);assertThrows(RuntimeException.class,()->access.authorize(q("DEPT",10),false));}
 @Test void centerCannotIncludeUnauthorizedChild(){when(orgMapper.selectList()).thenReturn(List.of(org(9,9,"CENTER"),org(10,9,"DEPT")));var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(9L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:center")).thenReturn(true);assertThrows(RuntimeException.class,()->access.authorize(q("CENTER",9),false));}
 @Test void userOutsideDataScopeRejected(){var user=new AdminUserRespDTO();user.setId(2L);user.setDeptId(10L);when(userApi.getUser(2L)).thenReturn(user);when(permissionApi.getDeptDataPermission(1L)).thenReturn(new DeptDataPermissionRespDTO());assertThrows(RuntimeException.class,()->access.authorize(q("USER",2),false));}
 @Test void tenantReadAllNeverAuthorizesTargetWrites(){when(permissionApi.getDeptDataPermission(1L)).thenReturn(new DeptDataPermissionRespDTO());assertFalse(access.commandDepartmentAllowed(10L));verify(permissionApi,never()).hasTenantReadAllAccess(any());}
 @Test void currentMembershipDoesNotGrantPreviousDepartmentHistory(){var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(10L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);assertTrue(access.historicalRowAllowed(q("USER",2),10L));assertFalse(access.historicalRowAllowed(q("USER",2),20L));assertFalse(access.historicalRowAllowed(q("USER",2),null));}
 @Test void tenantWideReaderCanReadUnattributedPersonalHistory(){when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);assertTrue(access.historicalRowAllowed(q("USER",2),null));}
 @Test void allDepartmentScopeCanReadUnattributedPersonalHistory(){var scope=new DeptDataPermissionRespDTO();scope.setAll(true);when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);assertTrue(access.historicalRowAllowed(q("USER",2),null));}
 @Test void missingScopeCannotReadUnattributedPersonalHistory(){assertFalse(access.historicalRowAllowed(q("USER",2),null));}
 @Test void tenantWideReaderCannotInventTeamAttribution(){assertFalse(access.historicalRowAllowed(q("DEPT",10),null));assertFalse(access.historicalRowAllowed(q("CENTER",9),null));}
 @Test void tenantWidePersonalQueryStillNeedsFeaturePermission(){var user=new AdminUserRespDTO();user.setId(2L);user.setDeptId(10L);when(userApi.getUser(2L)).thenReturn(user);when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);assertThrows(RuntimeException.class,()->access.authorize(q("USER",2),false));}
}
