package cn.iocoder.yudao.module.zsjos.service.homemetric.source;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricLeadPermissionGateTest {
 @InjectMocks HomeMetricSourcesImpl sources;
 @Mock PermissionApi permissions;
 @Mock ExistingPageMetricSources existing;
 @Mock BuiltinMetricSources builtin;
 MetricEvaluation.Operand op=new MetricEvaluation.Operand("a","lead",MetricAggregate.COUNT,null,MetricPeriod.Kind.ALL,false,null,MetricPeriod.Comparison.CURRENT);
 HomeMetricSpec spec(){return new HomeMetricSpec("test","测试","","",0,"test:read",List.of(),List.of(),"MODAL","a",false,0,List.of(new HomeMetricSpec.Operand(op,new AdvancedFilterGroupReqVO(),null)));}
 @Test void scopeGrantWithoutBasePageCannotReadOrDiscoverLeadSource(){
  lenient().when(permissions.hasAnyPermissions(7L,"zsjos:lead:query-submitted","zsjos:lead:query-owned","zsjos:lead:query-all")).thenReturn(true);
  assertFalse(sources.bind(spec(),7L).canRead(op));
  assertTrue(sources.catalog(7L).stream().noneMatch(s->s.key().equals("lead")));
 }
 @Test void basePageWithoutDataScopeDoesNotGrantSourceAccess(){
  when(permissions.hasAnyPermissions(7L,"zsjos:lead:query")).thenReturn(true);
  assertFalse(sources.bind(spec(),7L).canRead(op));
 }
 @Test void baseAndConfiguredScopeAllowSource(){
  when(permissions.hasAnyPermissions(7L,"zsjos:lead:query")).thenReturn(true);
  when(permissions.hasAnyPermissions(7L,"zsjos:lead:query-submitted","zsjos:lead:query-owned","zsjos:lead:query-all")).thenReturn(true);
  assertTrue(sources.bind(spec(),7L).canRead(op));
  assertTrue(sources.catalog(7L).stream().anyMatch(s->s.key().equals("lead")));
 }
 @Test void tenantReadAllStillRequiresBasePage(){
  lenient().when(permissions.hasTenantReadAllAccess(7L)).thenReturn(true);
  assertFalse(sources.bind(spec(),7L).canRead(op));
  when(permissions.hasAnyPermissions(7L,"zsjos:lead:query")).thenReturn(true);
  assertTrue(sources.bind(spec(),7L).canRead(op));
 }
}
