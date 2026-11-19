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
 @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceDetailMapper detailMapper;
 @Mock PermissionApi permissionApi; @Mock AdminUserApi userApi; @Mock PerformanceOrgMapper orgMapper;
 @Mock cn.iocoder.yudao.module.system.api.dept.PostApi postApi;
 @Mock cn.iocoder.yudao.module.zsjos.service.sales.SalesDepartmentBoundaryService salesBoundary;
 @Mock cn.iocoder.yudao.module.system.api.dept.DeptApi deptApi;
 MockedStatic<SecurityFrameworkUtils> security;
 @BeforeEach void setup(){cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);security=mockStatic(SecurityFrameworkUtils.class);security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(1L);
 lenient().when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(10,9,"DEPT")));
 lenient().when(userApi.getUser(1L)).thenReturn(person(1,0));
 lenient().when(salesBoundary.canAccessUser(anyLong(),anyLong())).thenReturn(true);
 lenient().when(salesBoundary.rootForDepartment(anyLong())).thenReturn(10L);
 lenient().when(salesBoundary.sameBoundary(anyLong(),anyLong())).thenReturn(true);
 lenient().when(salesBoundary.accessibleDepartmentIds(1L)).thenAnswer(inv->{
  if(permissionApi.hasTenantReadAllAccess(1L))return Set.of(10L,11L);
  var scope=permissionApi.getDeptDataPermission(1L);
  if(scope==null)return Set.of();
  return Boolean.TRUE.equals(scope.getAll())?Set.of(10L,11L):scope.getDeptIds()==null?Set.of():scope.getDeptIds();
 });}
 @AfterEach void close(){security.close();cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
 Query q(String type,long id){var q=new Query();q.setScopeType(type);q.setScopeId(id);return q;}
 AdminUserRespDTO person(long id,Integer status){var u=new AdminUserRespDTO();u.setId(id);u.setStatus(status);u.setDeptId(10L);return u;}
 @Test void bothTreesExcludeDisabledAndUnknownStatusUsers(){
  var enabled=person(2,0);
  when(permissionApi.getEnabledUserIdsByPermission("zsjos:sales:eligible")).thenReturn(Set.of(2L,3L,4L));
  when(userApi.getUserList(Set.of(2L,3L,4L))).thenReturn(List.of(enabled,person(3,1),person(4,null)));
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
 @Test void historicalPersonnelCannotReintroduceDeliveryOrMissingUsers() {
  var enabled=person(2,0);var delivery=person(3,0);delivery.setDeptId(20L);
  var disabled=person(5,1);var unknown=person(6,null);
  when(userApi.getUserList(Set.of(2L,3L,4L,5L,6L))).thenReturn(List.of(enabled,delivery,disabled,unknown));
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:department")).thenReturn(true);
  var a=new cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact();a.setUserId(2L);a.setUserName("历史销售");
  var b=new cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact();b.setUserId(3L);b.setUserName("交付人员");
  var c=new cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact();c.setUserId(4L);c.setUserName("不存在人员");
  var d=new cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact();d.setUserId(5L);d.setUserName("停用人员");
  var e=new cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact();e.setUserId(6L);e.setUserName("状态未知人员");
  when(detailMapper.historicalPeople(any())).thenReturn(List.of(a,b,c,d,e));
  assertEquals(List.of(2L),access.tree(false).stream().filter(n->"USER".equals(n.scopeType())).map(n->n.scopeId()).toList());
  enabled.setStatus(1);
  assertTrue(access.tree(false).stream().noneMatch(n->"USER".equals(n.scopeType())));
  enabled.setStatus(0);
  assertEquals(List.of(2L),access.tree(false).stream().filter(n->"USER".equals(n.scopeType())).map(n->n.scopeId()).toList());
 }
 @Test void presentationMembershipUsesConfiguredDepartmentsAndPropagatesSystemFailures() {
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  var enabled=person(2,0);var unconfigured=person(3,0);unconfigured.setDeptId(999L);
  when(userApi.getUserList(Set.of(2L,3L,4L))).thenReturn(List.of(enabled,unconfigured));
  assertEquals(List.of(2L),access.presentationUsers(Set.of(2L,3L,4L)).stream().map(AdminUserRespDTO::getId).toList());
  assertTrue(access.presentationUsers(Set.of()).isEmpty());
  when(userApi.getUserList(Set.of(2L,3L,4L))).thenThrow(new IllegalStateException("System unavailable"));
  assertThrows(IllegalStateException.class,()->access.presentationUsers(Set.of(2L,3L,4L)));
  verifyNoInteractions(postApi);
 }
 @Test void disabledTargetCannotBeSavedDespiteReadAndWritePermissions(){
  when(userApi.getUser(2L)).thenReturn(person(2,1));
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance-target:query")).thenReturn(true);
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  var ex=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->access.targetWriteObject("USER",2L));
  assertTrue(ex.getMessage().contains("停用"));
 }
 @Test void targetWriteRejectsEnabledUserWithoutSalesQualification(){
  when(userApi.getUser(2L)).thenReturn(person(2,0));
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance-target:query")).thenReturn(true);
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  when(permissionApi.getEnabledUserIdsByPermission("zsjos:sales:eligible")).thenReturn(Set.of());
  var ex=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->access.targetWriteObject("USER",2L));
  assertTrue(ex.getMessage().contains("销售业务资格"));
 }
 @Test void selfTargetWriteRejectsMissingBoundaryEvenWithSelfDataScope(){
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance-target:query")).thenReturn(true);
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  when(permissionApi.getEnabledUserIdsByPermission("zsjos:sales:eligible")).thenReturn(Set.of(1L));
  when(salesBoundary.rootForDepartment(10L)).thenThrow(new IllegalStateException("missing boundary"));
  assertThrows(IllegalStateException.class,()->access.targetWriteObject("USER",1L));
 }
 PerformanceOrgDO org(long id,long center,String kind){var o=new PerformanceOrgDO();o.setDeptId(id);o.setCenterId(center);o.setKind(kind);o.setBusinessDomain("sales");return o;}
 @Test void currentFollowUpUsesEnabledSalesCurrentDepartmentsAndDeduplicatesCenter(){
  when(permissionApi.getEnabledUserIdsByPermission("zsjos:sales:eligible")).thenReturn(Set.of(2L,3L,4L));
  var moved=person(3,0);moved.setDeptId(11L);
  when(userApi.getUserList(Set.of(2L,3L,4L))).thenReturn(List.of(person(2,0),moved,person(4,1),person(2,0)));
  when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(10,20,"DEPT"),org(11,20,"DEPT"),org(20,20,"CENTER")));
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  when(permissionApi.hasAnyPermissions(eq(1L),anyString())).thenReturn(true);
  assertEquals(Set.of(2L),access.currentFollowUpAssignees(q("DEPT",10)));
  assertEquals(Set.of(3L),access.currentFollowUpAssignees(q("DEPT",11)));
  assertEquals(Set.of(2L,3L),access.currentFollowUpAssignees(q("CENTER",20)));
  when(userApi.getUser(4L)).thenReturn(person(4,1));
  when(userApi.getUser(5L)).thenReturn(person(5,0));
  assertTrue(access.currentFollowUpAssignees(q("USER",4)).isEmpty());
  assertTrue(access.currentFollowUpAssignees(q("USER",5)).isEmpty());
  when(userApi.getUserList(Set.of(2L,3L,4L))).thenThrow(new IllegalStateException("user API unavailable"));
  assertThrows(IllegalStateException.class,()->access.currentFollowUpAssignees(q("DEPT",10)));
 }
 @Test void currentSelfIsBoundWithoutSalesMembership(){
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:self")).thenReturn(true);
  assertEquals(Set.of(1L),access.currentFollowUpAssignees(q("SELF",999)));
  verifyNoInteractions(postApi,userApi);
 }
 @Test void currentTasksStillRejectUnauthorizedDepartment(){
  when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(10,20,"DEPT")));
  assertThrows(RuntimeException.class,()->access.currentFollowUpAssignees(q("DEPT",10)));
  verifyNoInteractions(postApi,userApi);
 }
 @Test void currentDepartmentWithNoSalesPostReturnsEmpty(){
  when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(10,20,"DEPT")));
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:department")).thenReturn(true);
  assertTrue(access.currentFollowUpAssignees(q("DEPT",10)).isEmpty());
 }
 @Test void selfCannotSpoofUser(){when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:self")).thenReturn(true);assertEquals(1L,access.authorize(q("SELF",999),false).getScopeId());}
 @Test void selfNeedsViewPermission(){assertThrows(RuntimeException.class,()->access.authorize(q("SELF",1),false));}
 @Test void featurePermissionDoesNotBypassDataScope(){when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(10,9,"DEPT")));when(permissionApi.getDeptDataPermission(1L)).thenReturn(new DeptDataPermissionRespDTO());assertThrows(RuntimeException.class,()->access.authorize(q("DEPT",10),false));}
 @Test void scopedDepartmentAllowed(){when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(10,9,"DEPT")));var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(10L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:department")).thenReturn(true);assertDoesNotThrow(()->access.authorize(q("DEPT",10),false));}
 @Test void tenantReadAllStillNeedsFeature(){when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(10,9,"DEPT")));when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);assertThrows(RuntimeException.class,()->access.authorize(q("DEPT",10),false));}
 @Test void centerCannotIncludeUnauthorizedChild(){when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(9,9,"CENTER"),org(10,9,"DEPT")));var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(9L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);assertThrows(RuntimeException.class,()->access.authorize(q("CENTER",9),false));}
 @Test void userOutsideDataScopeRejected(){var user=new AdminUserRespDTO();user.setId(2L);user.setDeptId(10L);when(userApi.getUser(2L)).thenReturn(user);when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:department")).thenReturn(true);when(permissionApi.getDeptDataPermission(1L)).thenReturn(new DeptDataPermissionRespDTO());assertThrows(RuntimeException.class,()->access.authorize(q("USER",2),false));}
 @Test void tenantReadAllNeverAuthorizesTargetWrites(){when(permissionApi.getDeptDataPermission(1L)).thenReturn(new DeptDataPermissionRespDTO());assertFalse(access.commandDepartmentAllowed(10L));}
 @Test void currentMembershipDoesNotGrantPreviousDepartmentHistory(){var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(10L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);assertTrue(access.historicalRowAllowed(q("USER",2),10L));assertFalse(access.historicalRowAllowed(q("USER",2),20L));assertFalse(access.historicalRowAllowed(q("USER",2),null));}
 @Test void tenantWideReaderCanReadUnattributedPersonalHistory(){when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);assertTrue(access.historicalRowAllowed(q("USER",2),null));}
 @Test void ordinaryAllDepartmentScopeCannotReadUnattributedPersonalHistory(){assertFalse(access.historicalRowAllowed(q("USER",2),null));}
 @Test void missingScopeCannotReadUnattributedPersonalHistory(){assertFalse(access.historicalRowAllowed(q("USER",2),null));}
 @Test void tenantWideReaderCannotInventTeamAttribution(){assertFalse(access.historicalRowAllowed(q("DEPT",10),null));assertFalse(access.historicalRowAllowed(q("CENTER",9),null));}
 @Test void tenantWidePersonalQueryStillNeedsFeaturePermission(){var user=new AdminUserRespDTO();user.setId(2L);user.setDeptId(10L);when(userApi.getUser(2L)).thenReturn(user);assertThrows(RuntimeException.class,()->access.authorize(q("USER",2),false));}

 @Test void sqlHistoricalScopeMatchesPerRowAuthorization() {
  for (boolean tenantAll : List.of(false,true)) for (boolean dataAll : List.of(false,true))
   for (boolean ownPermission : List.of(false,true)) for (boolean present : List.of(false,true)) {
    lenient().when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(tenantAll);
    var data=new DeptDataPermissionRespDTO(); data.setAll(dataAll); data.setDeptIds(Set.of(10L));
    lenient().when(permissionApi.getDeptDataPermission(1L)).thenReturn(present?data:null);
    lenient().when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:self")).thenReturn(ownPermission);
    for(String type:List.of("SELF","USER","DEPT","CENTER")) for(long id:List.of(1L,2L)) {
     var query=q(type,id);var projected=access.historicalScope(query);
     for(Long dept:Arrays.asList(null,10L,20L)) {
      boolean sqlAllowed=projected.allDepartments()?(dept!=null||projected.missingDepartment()):dept!=null && projected.departments().contains(dept);
      assertEquals(access.historicalRowAllowed(query,dept),sqlAllowed,type+" historical projection");
     }
    }
   }
 }
 @Test void transferredDisabledUserCannotBypassCurrentBoundary() {
  var moved=person(2,1);moved.setDeptId(999L);when(userApi.getUser(2L)).thenReturn(moved);
  var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(10L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);
  when(salesBoundary.canAccessUser(1L,2L)).thenReturn(false);
  assertThrows(RuntimeException.class,()->access.authorize(q("USER",2),false));
  assertEquals(Set.of(10L),access.reportScope(q("USER",2)).departments());
  assertFalse(access.reportScope(q("USER",2)).missingDepartment());
  verify(orgMapper,never()).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
 }
 @Test void personalKnownDomainCanRetainMissingDepartment() {
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:self")).thenReturn(true);
  assertTrue(access.reportScope(q("USER",1)).missingDepartment());
 }
 @Test void centerLeaderAboveSalesRootsKeepsSystemScopeWithoutBoundaryError() {
  when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(9,9,"CENTER"),org(10,9,"DEPT"),org(11,9,"DEPT")));
  var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(9L,10L,11L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:center")).thenReturn(true);
  assertDoesNotThrow(()->access.authorize(q("CENTER",9),false));
  assertTrue(access.departmentAllowed(10L));assertFalse(access.departmentAllowed(12L));
  assertTrue(access.targetVisibility().test(null,9L),"center-level target row");
  assertTrue(access.commandDepartmentAllowed(9L),"center-level target write");
  verify(salesBoundary,never()).rootForDepartment(anyLong());verify(salesBoundary,never()).accessibleDepartmentIds(anyLong());
 }
 @Test void tenantWideAdministratorSeesHistoricalAndDeletedDepartments() {
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  assertTrue(access.departmentAllowed(999L));
  var team=access.historicalScope(q("DEPT",10));assertTrue(team.allDepartments());assertFalse(team.missingDepartment());
  assertTrue(access.historicalScope(q("USER",2)).missingDepartment());
  assertTrue(access.historicalRowAllowed(q("DEPT",10),999L));
  verify(salesBoundary,never()).accessibleDepartmentIds(anyLong());
 }
 @Test void salesActorIsNarrowedToBoundaryEvenWithAllDataScope() {
  when(salesBoundary.isSalesActor(1L)).thenReturn(true);
  when(salesBoundary.accessibleDepartmentIds(1L)).thenReturn(Set.of(10L));
  var scope=new DeptDataPermissionRespDTO();scope.setAll(true);lenient().when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);
  assertTrue(access.departmentAllowed(10L));assertFalse(access.departmentAllowed(11L));
  var projected=access.historicalScope(q("DEPT",10));assertFalse(projected.allDepartments());assertEquals(Set.of(10L),projected.departments());
  assertFalse(access.historicalRowAllowed(q("USER",2),null));
  assertTrue(access.commandDepartmentAllowed(10L));assertFalse(access.commandDepartmentAllowed(11L));
 }
 @Test void centerTargetRowNeedsEveryCenterDepartment() {
  when(orgMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(org(9,9,"CENTER"),org(10,9,"DEPT"),org(11,9,"DEPT")));
  when(salesBoundary.isSalesActor(1L)).thenReturn(true);
  when(salesBoundary.accessibleDepartmentIds(1L)).thenReturn(Set.of(10L,11L));
  assertTrue(access.targetVisibility().test(null,9L));
  assertFalse(access.targetVisibility().test(2L,9L),"personal rows are never widened by the center rule");
  when(salesBoundary.accessibleDepartmentIds(1L)).thenReturn(Set.of(10L));
  assertFalse(access.targetVisibility().test(null,9L));
 }
 @Test void personalTargetReadUsesStoredSnapshotDepartment() {
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance-target:query")).thenReturn(true);
  var scope=new DeptDataPermissionRespDTO();scope.setDeptIds(Set.of(10L));when(permissionApi.getDeptDataPermission(1L)).thenReturn(scope);
  var row=new cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.PerformanceTargetDO();row.setBusinessDomain("sales");row.setScopeType("USER");row.setScopeId(2L);row.setDeptId(10L);
  assertDoesNotThrow(()->access.targetReadObject(row));
  row.setDeptId(20L);
  assertThrows(RuntimeException.class,()->access.targetReadObject(row));
  verify(userApi,never()).getUser(2L);
 }
 @Test void unclassifiedRequiresAllThreePermissionsAndTenantPrivilegeForOthers() {
  for(String action:List.of("query","self","detail"))when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:"+action)).thenReturn(true);
  assertEquals(1L,access.authorizeUnclassified(null));
  assertThrows(RuntimeException.class,()->access.authorizeUnclassified(2L));
  when(permissionApi.hasTenantReadAllAccess(1L)).thenReturn(true);
  assertEquals(2L,access.authorizeUnclassified(2L));
  when(permissionApi.hasAnyPermissions(1L,"zsjos:sales-performance:detail")).thenReturn(false);
  assertThrows(RuntimeException.class,()->access.authorizeUnclassified(1L));
 }
}
