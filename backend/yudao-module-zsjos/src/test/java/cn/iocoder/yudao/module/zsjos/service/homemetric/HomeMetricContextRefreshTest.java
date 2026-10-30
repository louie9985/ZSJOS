package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.HomeMetricMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.homemetric.HomeMetricDO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class HomeMetricContextRefreshTest {
 @Spy @InjectMocks HomeMetricConfigService service;
 @Mock HomeMetricMapper mapper;
 @Mock StringRedisTemplate redis;
 @Mock ValueOperations<String,String> values;
 @Mock HomeMetricSources sources;
 @Mock HomeMetricDictionarySnapshots snapshots;
 @Mock MetricEvaluation.Sources bound;
 @Mock cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
 String token="12345678-1234-1234-1234-123456789abc";
 MetricPeriod.Range range=new MetricPeriod.Range(LocalDateTime.of(2020,1,1,0,0),LocalDateTime.of(2020,1,2,0,0));
 HomeMetricSpec spec;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);
  var a=new MetricEvaluation.Operand("a","order",MetricAggregate.COUNT,null,MetricPeriod.Kind.DAY,false,null,MetricPeriod.Comparison.CURRENT);
  var b=new MetricEvaluation.Operand("b","order",MetricAggregate.COUNT,null,MetricPeriod.Kind.DAY,false,null,MetricPeriod.Comparison.CURRENT);
  spec=new HomeMetricSpec("test","当前可读比率",null,null,1,"test:view",List.of(7L,8L),List.of(),"MODAL","a/b",true,2,List.of(new HomeMetricSpec.Operand(a,null,"submittedAt"),new HomeMetricSpec.Operand(b,null,"submittedAt"))).withSnapshots(Map.of("snapshot","历史标签"));
  var row=new HomeMetricDO();row.setId(1L);row.setPublishedVersion(2);row.setEnabled(true);
  when(mapper.selectById(1L)).thenReturn(row);when(redis.opsForValue()).thenReturn(values);
  var stale=new HomeMetricConfigService.Evaluation(Instant.parse("2020-01-01T01:00:00Z"),new HomeMetricConfigService.Value("OK","999"),MetricUnit.scalar(),List.of(new HomeMetricConfigService.Input("a","订单",range,new HomeMetricConfigService.Value("OK","999")),new HomeMetricConfigService.Input("b","订单",range,new HomeMetricConfigService.Value("OK","1"))));
  var context=new HomeMetricConfigService.Context(1L,2,7L,1L,spec,stale,Instant.now().plusSeconds(900));
  when(values.get("zsjos:home-metric:context:"+token)).thenReturn(JsonUtils.toJsonString(context));
  doReturn(true).when(service).visible(any(HomeMetricSpec.class),eq(7L));when(permissions.isPermissionDefined("test:view")).thenReturn(true);
  when(sources.bind(any(),eq(7L))).thenReturn(bound);
 }
 @AfterEach void cleanup(){TenantContextHolder.clear();}
 void metadata(){
  when(bound.canRead(any())).thenReturn(true);when(bound.validate(any())).thenReturn(MetricUnit.of("COUNT"));
  when(sources.catalog(7L)).thenReturn(List.of(new HomeMetricSources.Source("order","订单","order",List.of(),List.of(),"/orders")));
 }
 void readable(){metadata();when(bound.aggregate(any(),eq(range),any(Duration.class))).thenAnswer(i->MetricFormula.Result.ok(new BigDecimal(((MetricEvaluation.Operand)i.getArgument(0)).name().equals("a")?"2":"4")));}
 @Test void narrowedScopeRecalculatesEveryOperandWithoutChangingFrozenRange(){
  readable();var fresh=service.context(token,7L);
  assertEquals("0.5",fresh.evaluation().result().value());assertEquals(range,fresh.evaluation().inputs().getFirst().range());assertNotEquals("999",fresh.evaluation().inputs().getFirst().result().value());
  assertEquals(Map.of("snapshot","历史标签"),fresh.spec().dictionarySnapshots());verify(bound,times(2)).aggregate(any(),eq(range),any(Duration.class));
 }
 @Test void contextViewAndDetailsReuseSameFrozenRangeWithCurrentScope(){
  readable();when(sources.details(any(),eq(range),eq(7L),eq(1),eq(20))).thenReturn(PageResult.empty());
  assertEquals("0.5",service.contextView(token,7L).evaluation().result().value());service.details(new HomeMetricConfigService.Details(token,"a",1,20),7L);
  verify(bound,times(4)).aggregate(any(),eq(range),any(Duration.class));verify(sources).details(any(),eq(range),eq(7L),eq(1),eq(20));
 }
 @Test void revokedSourceFeatureRejectsRatherThanReturningCachedNumbers(){
  when(bound.canRead(any())).thenReturn(false);
  var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.contextView(token,7L));assertEquals(HOME_METRIC_DENIED.getCode(),error.getCode());verify(bound,never()).aggregate(any(),any(),any(Duration.class));
 }
 @Test void missingOperandHasStableBusinessError(){
  readable();var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.details(new HomeMetricConfigService.Details(token,"missing",1,20),7L));assertEquals(HOME_METRIC_OPERAND_NOT_FOUND.getCode(),error.getCode());
 }
 @Test void timeoutDropsCachedValuesAndDetailsReportsQueryFailureInsteadOfMissingOperand(){
  metadata();when(bound.aggregate(any(),eq(range),any(Duration.class))).thenThrow(new org.springframework.dao.QueryTimeoutException("test timeout"));
  var view=service.contextView(token,7L);assertEquals("TIMEOUT",view.evaluation().result().status());assertNull(view.evaluation().result().value());assertTrue(view.evaluation().inputs().isEmpty());
  var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.details(new HomeMetricConfigService.Details(token,"a",1,20),7L));assertEquals(HOME_METRIC_QUERY_TIMEOUT.getCode(),error.getCode());
 }
}
