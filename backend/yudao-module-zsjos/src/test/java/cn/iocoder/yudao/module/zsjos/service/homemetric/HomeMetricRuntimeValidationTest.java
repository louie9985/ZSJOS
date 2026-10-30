package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HomeMetricRuntimeValidationTest {
 @InjectMocks HomeMetricConfigService service;
 @Mock PermissionApi permissions;
 @Mock AdminUserApi users;
 @Mock DeptApi departments;
 @Mock HomeMetricSources sources;
 @Mock MetricEvaluation.Sources bound;
 @Mock AdvancedFilterService filters;
 HomeMetricSpec spec(){return new HomeMetricSpec("test","测试",null,null,1,"test:view",List.of(7L,8L),List.of(20L),"MODAL","a",false,0,List.of(new HomeMetricSpec.Operand(new MetricEvaluation.Operand("a","lead",MetricAggregate.COUNT,null,MetricPeriod.Kind.ALL,false,null,MetricPeriod.Comparison.CURRENT),null,null)));}
 void validDefinition(){when(permissions.isPermissionDefined("test:view")).thenReturn(true);when(sources.bind(any(),eq(7L))).thenReturn(bound);when(bound.validate(any())).thenReturn(MetricUnit.of("COUNT"));}
 @Test void runtimeDoesNotCheckOtherAudienceMembersEnabledState(){validDefinition();assertDoesNotThrow(()->service.validateRuntime(spec(),7L));verifyNoInteractions(users,departments);}
 @Test void editingRejectsDisabledAudienceThroughSingleBatchLookup(){
  validDefinition();var active=new AdminUserRespDTO();active.setId(7L);active.setStatus(0);var disabled=new AdminUserRespDTO();disabled.setId(8L);disabled.setStatus(1);
  when(users.getUserList(anyCollection())).thenReturn(List.of(active,disabled));assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.validate(spec(),7L));verify(users).getUserList(Set.of(7L,8L));verify(users,never()).getUser(anyLong());
 }
 @Test void freezingRetainsTrustedDictionarySnapshots() throws Exception {
  var s=spec().withSnapshots(Map.of("a:kind:type:x","历史名称"));var method=HomeMetricConfigService.class.getDeclaredMethod("freeze",HomeMetricSpec.class,java.time.Instant.class);method.setAccessible(true);
  var frozen=(HomeMetricSpec)method.invoke(service,s,java.time.Instant.now());assertEquals(s.dictionarySnapshots(),frozen.dictionarySnapshots());
 }
}
