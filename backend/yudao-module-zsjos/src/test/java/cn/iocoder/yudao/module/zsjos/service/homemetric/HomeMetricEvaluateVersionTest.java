package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.homemetric.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HomeMetricEvaluateVersionTest {
 @Spy @InjectMocks HomeMetricConfigService service;
 @Mock HomeMetricMapper mapper;
 @Mock HomeMetricVersionMapper versions;
 @Mock StringRedisTemplate redis;
 @Mock ValueOperations<String,String> values;
 @Mock HomeMetricSources sources;
 @Mock HomeMetricDictionarySnapshots snapshots;
 @Mock AdvancedFilterService filters;
 @Mock MetricEvaluation.Sources bound;
 HomeMetricDO row;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(1L);
  var spec=new HomeMetricSpec("test","发布版2",null,null,1,"test:view",List.of(),List.of(),"MODAL","a",false,0,List.of(new HomeMetricSpec.Operand(new MetricEvaluation.Operand("a","lead",MetricAggregate.COUNT,null,MetricPeriod.Kind.ALL,false,null,MetricPeriod.Comparison.CURRENT),null,null)));
  row=new HomeMetricDO();row.setId(1L);row.setVersion(3);row.setPublishedVersion(2);row.setEnabled(true);row.setDraftJson("not-used");when(mapper.selectById(1L)).thenReturn(row);
  var version=new HomeMetricVersionDO();version.setMetricId(1L);version.setVersion(2);version.setSpecJson(JsonUtils.toJsonString(spec));when(versions.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(version);
  doReturn(true).when(service).visible(any(HomeMetricSpec.class),eq(7L));doNothing().when(service).validateRuntime(any(),eq(7L));when(snapshots.capture(any(),any(),eq(7L))).thenAnswer(i->i.getArgument(0));
  when(sources.bind(any(),eq(7L))).thenReturn(bound);when(bound.validate(any())).thenReturn(MetricUnit.of("COUNT"));when(bound.canRead(any())).thenReturn(true);when(bound.aggregate(any(),isNull(),any(java.time.Duration.class))).thenReturn(MetricFormula.Result.ok(BigDecimal.TEN));
  when(sources.catalog(7L)).thenReturn(List.of(new HomeMetricSources.Source("lead","客资","lead",List.of(),List.of(),"/leads")));
 }
 @AfterEach void cleanup(){TenantContextHolder.clear();}
 @Test void resultCarriesItsOwnPublishedVersionAndPublicSpec(){
  when(redis.opsForValue()).thenReturn(values);var result=service.evaluate(1L,7L);
  assertEquals(2,result.publishedVersion());assertEquals("发布版2",result.spec().name());assertEquals("10",result.evaluation().result().value());assertNotNull(result.contextId());assertNull(result.spec().viewPermission());
 }
 @Test void publicationDuringEvaluationRejectsMixedVersionResult(){
  var newer=new HomeMetricDO();newer.setId(1L);newer.setPublishedVersion(4);newer.setEnabled(true);when(mapper.selectById(1L)).thenReturn(row,newer);
  var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.evaluate(1L,7L));assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_CONTEXT_EXPIRED.getCode(),error.getCode());verifyNoInteractions(redis);
 }
}
