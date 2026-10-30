package cn.iocoder.yudao.module.zsjos.service.homemetric.source;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.MetricSourceMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import cn.iocoder.yudao.module.zsjos.service.order.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class HomeMetricSourcesTest {
 @InjectMocks HomeMetricSourcesImpl service;
 @Mock PermissionApi permissions;
 @Mock AdvancedFilterService filters;
 @Mock SalesOrderObjectPermissionService orders;
 @Mock MetricSourceMapper mapper;
 @Mock MetricSourceDeadline deadlines;
 @Mock ExistingPageMetricSources existing;
 @Mock BuiltinMetricSources builtin;
 @BeforeEach void before(){TenantContextHolder.setTenantId(1L);}
 @AfterEach void after(){TenantContextHolder.clear();}
 HomeMetricSpec spec(String field) {
  var calculation=new MetricEvaluation.Operand("a","order",MetricAggregate.SUM,field,MetricPeriod.Kind.ALL,false,null,MetricPeriod.Comparison.CURRENT);
  return new HomeMetricSpec("test","测试","","元",0,"test:read",List.of(),List.of(),"MODAL","a",false,2,List.of(new HomeMetricSpec.Operand(calculation,new AdvancedFilterGroupReqVO(),null)));
 }
 @Test void validatesFieldsRatherThanInterpolatingClientSql(){var spec=spec("totalAmount) FROM secret --");assertThrows(IllegalArgumentException.class,()->service.bind(spec,7L).validate(spec.operands().getFirst().calculation()));}
 @Test void deniedAggregateDoesNotQueryDatabase(){var spec=spec("totalAmount");assertEquals(MetricFormula.Status.FORBIDDEN,service.bind(spec,7L).aggregate(spec.operands().getFirst().calculation(),null).status());verifyNoInteractions(mapper,orders);}
 @Test void orderAggregateUsesManagementSubmitterScopeAndTenant(){
  when(permissions.hasAnyPermissions(7L,"zsjos:sales-order:query-management")).thenReturn(true);
  when(orders.resolveManagementScope(7L)).thenReturn(new SalesOrderManagementScope(false,true,Set.of(),Set.of(7L)));
  when(mapper.aggregate(any())).thenReturn(new BigDecimal("12.50"));
  var spec=spec("totalAmount");var result=service.bind(spec,7L).aggregate(spec.operands().getFirst().calculation(),null);
  assertEquals(new BigDecimal("12.50"),result.value());
  var capture=ArgumentCaptor.forClass(AdvancedFilterQuery.class);verify(mapper).aggregate(capture.capture());
  assertTrue(capture.getValue().getWhereSql().contains("SUM(v)"));
  assertTrue(capture.getValue().getWhereSql().contains("o.submitter_user_id IN"));
  assertEquals(1L,capture.getValue().getParameters().get("tenantId"));
  assertEquals(7L,capture.getValue().getParameters().get("metric_owner0"));
 }
 @Test void detailAmountRetainsPrecisionAsDecimalText(){
  when(deadlines.execute(any(),any())).thenAnswer(call->((java.util.function.Supplier<?>)call.getArgument(1)).get());
  when(permissions.hasAnyPermissions(7L,"zsjos:sales-order:query-management")).thenReturn(true);
  when(orders.resolveManagementScope(7L)).thenReturn(new SalesOrderManagementScope(false,true,Set.of(),Set.of(7L)));
  when(mapper.aggregate(any())).thenReturn(BigDecimal.ONE);
  when(mapper.details(any())).thenReturn(List.of(Map.of("id",1L,"v",new BigDecimal("9007199254740993.12"))));
  var result=service.details(spec("totalAmount").operands().getFirst(),null,7L,1,10);
  assertEquals("9007199254740993.12",result.getList().getFirst().get("v"));
  assertEquals(1L,result.getList().getFirst().get("id"));
 }
}
