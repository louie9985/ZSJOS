package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.homemetric.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class HomeMetricContextTest {
 @Spy @InjectMocks HomeMetricConfigService service;
 @Mock HomeMetricMapper mapper;
 @Mock StringRedisTemplate redis;
 @Mock ValueOperations<String,String> values;
 @Mock HomeMetricSources sources;
 @Mock HomeMetricDictionarySnapshots snapshots;
 String token="12345678-1234-1234-1234-123456789abc";
 HomeMetricDO row;
 HomeMetricSpec spec=new HomeMetricSpec("test","指标",null,null,1,"test:view",List.of(),List.of(),"MODAL","a",false,0,List.of());
 @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);row=new HomeMetricDO();row.setId(1L);row.setVersion(2);row.setPublishedVersion(2);row.setEnabled(true);}
 @AfterEach void cleanup(){TenantContextHolder.clear();}
 void stored(Long actor,Long tenant,int version,Instant expiry){
  when(redis.opsForValue()).thenReturn(values);lenient().when(mapper.selectById(1L)).thenReturn(row);
  var result=new HomeMetricConfigService.Evaluation(Instant.now(),new HomeMetricConfigService.Value("OK","2"),MetricUnit.scalar(),List.of());
  when(values.get("zsjos:home-metric:context:"+token)).thenReturn(JsonUtils.toJsonString(new HomeMetricConfigService.Context(1L,version,actor,tenant,spec,result,expiry)));
 }
 @Test void anotherUserCannotReuseToken(){stored(8L,1L,2,Instant.now().plusSeconds(60));assertThrows(RuntimeException.class,()->service.context(token,7L));verifyNoInteractions(sources);}
 @Test void anotherTenantCannotReuseToken(){stored(7L,2L,2,Instant.now().plusSeconds(60));assertThrows(RuntimeException.class,()->service.context(token,7L));verifyNoInteractions(sources);}
 @Test void expiredTokenIsRejected(){stored(7L,1L,2,Instant.now().minusSeconds(1));assertThrows(RuntimeException.class,()->service.context(token,7L));verifyNoInteractions(sources);}
 @Test void republishedVersionInvalidatesOldToken(){stored(7L,1L,1,Instant.now().plusSeconds(60));assertThrows(RuntimeException.class,()->service.context(token,7L));verifyNoInteractions(sources);}
 @Test void disabledMetricInvalidatesToken(){row.setEnabled(false);stored(7L,1L,2,Instant.now().plusSeconds(60));assertThrows(RuntimeException.class,()->service.context(token,7L));verifyNoInteractions(sources);}
 @Test void revokedAudienceInvalidatesToken(){stored(7L,1L,2,Instant.now().plusSeconds(60));doReturn(false).when(service).visible(any(HomeMetricSpec.class),eq(7L));assertThrows(RuntimeException.class,()->service.context(token,7L));verifyNoInteractions(sources);}
 @Test void missingRedisValueHasStableFailure(){when(redis.opsForValue()).thenReturn(values);assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.context(token,7L));verifyNoInteractions(mapper,sources);}
}
