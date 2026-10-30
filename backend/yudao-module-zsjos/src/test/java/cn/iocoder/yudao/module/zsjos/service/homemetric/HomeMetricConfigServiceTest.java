package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.homemetric.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HomeMetricConfigServiceTest {
 @BeforeAll static void initializeTable(){com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"home-metric-test"),HomeMetricDO.class);}
 @InjectMocks HomeMetricConfigService service;
 @Mock PermissionApi permissions;
 @Mock AdminUserApi users;
 @Mock HomeMetricMapper mapper;
 @Mock HomeMetricVersionMapper versions;
 HomeMetricSpec spec(List<Long> userIds,List<Long> deptIds){return new HomeMetricSpec("test","指标",null,null,1,"test:view",userIds,deptIds,"MODAL","a",false,0,List.of());}
 @Test void undefinedPermissionNeverBecomesVisibleThroughWildcard(){
  assertFalse(service.visible(spec(List.of(),List.of()),7L));verify(permissions,never()).hasAnyPermissions(any(),any(String[].class));
 }
 @Test void userAndDepartmentRestrictionsAreIntersections(){
  when(permissions.isPermissionDefined("test:view")).thenReturn(true);when(permissions.hasAnyPermissions(7L,"test:view")).thenReturn(true);
  var user=new AdminUserRespDTO();user.setId(7L);user.setStatus(0);user.setDeptId(20L);when(users.getUser(7L)).thenReturn(user);
  assertTrue(service.visible(spec(List.of(7L),List.of(20L)),7L));
  assertFalse(service.visible(spec(List.of(8L),List.of(20L)),7L));
  assertFalse(service.visible(spec(List.of(7L),List.of(21L)),7L));
 }
 @Test void staleDisableDoesNotUpdateOrTouchVersions(){
  var row=new HomeMetricDO();row.setId(1L);row.setVersion(2);when(mapper.selectById(1L)).thenReturn(row);
  when(permissions.hasAnyPermissions(7L,"zsjos:home-metric:publish")).thenReturn(true);
  assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.disable(new HomeMetricConfigService.Command(1L,1,null),7L));
  verify(mapper,never()).update(any(),any());verifyNoInteractions(versions);
 }
 @Test void disabledMetricDoesNotReachQueryOrRedis(){
  var row=new HomeMetricDO();row.setId(1L);row.setEnabled(false);when(mapper.selectById(1L)).thenReturn(row);
  assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.evaluate(1L,7L));
 }
 @Test void viewerProjectionNeverExposesAudienceOrFilterPayload(){
  when(permissions.isPermissionDefined("test:view")).thenReturn(true);when(permissions.hasAnyPermissions(7L,"test:view")).thenReturn(true);
  var user=new AdminUserRespDTO();user.setId(7L);user.setStatus(0);user.setDeptId(20L);when(users.getUser(7L)).thenReturn(user);
  var filter=new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO();filter.setLogic("AND");filter.setConditions(List.of());filter.setGroups(List.of());
  var calculation=new MetricEvaluation.Operand("a","lead",MetricAggregate.COUNT,null,MetricPeriod.Kind.ALL,false,null,MetricPeriod.Comparison.CURRENT);
  var spec=new HomeMetricSpec("test","指标",null,null,1,"test:view",List.of(7L),List.of(20L),"MODAL","a",false,0,List.of(new HomeMetricSpec.Operand(calculation,filter,null))).withSnapshots(Map.of("secret-key","历史标签"));
  var row=new HomeMetricDO();row.setId(1L);row.setVersion(2);row.setPublishedVersion(2);row.setEnabled(true);
  var version=new HomeMetricVersionDO();version.setMetricId(1L);version.setVersion(2);version.setSpecJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(spec));
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(row));
  when(versions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(version));
  var result=service.visible(7L).getFirst().spec();assertNull(result.viewPermission());assertTrue(result.userIds().isEmpty());assertTrue(result.deptIds().isEmpty());assertNull(result.operands().getFirst().filter());assertTrue(result.dictionarySnapshots().isEmpty());
  verify(users,times(1)).getUser(7L);verify(versions,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));verify(versions,never()).selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
 }
 @Test void legacyOverCapacityIsFilteredAndSortedBeforeTwelveSlotProjection(){
  when(permissions.isPermissionDefined("test:view")).thenReturn(true);when(permissions.hasAnyPermissions(7L,"test:view")).thenReturn(true);
  var user=new AdminUserRespDTO();user.setId(7L);user.setStatus(0);user.setDeptId(20L);when(users.getUser(7L)).thenReturn(user);
  List<HomeMetricDO> rows=new ArrayList<>();List<HomeMetricVersionDO> published=new ArrayList<>();
  for(int i=0;i<21;i++){
   var row=new HomeMetricDO();row.setId((long)i+1);row.setVersion(1);row.setPublishedVersion(1);row.setEnabled(true);rows.add(row);
   var spec=new HomeMetricSpec("test"+i,"指标"+i,null,null,20-i,"test:view",List.of(i<8?8L:7L),List.of(),"MODAL","a",false,0,List.of());
   var v=new HomeMetricVersionDO();v.setMetricId(row.getId());v.setVersion(1);v.setSpecJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(spec));published.add(v);
  }
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(rows);when(versions.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(published);
  var result=service.visible(7L);assertEquals(12,result.size());assertEquals(21L,result.getFirst().id());assertEquals(10L,result.getLast().id());
  verify(users,times(1)).getUser(7L);verify(permissions,times(1)).isPermissionDefined("test:view");verify(versions,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));verify(mapper,never()).update(any(),any());
 }
}
