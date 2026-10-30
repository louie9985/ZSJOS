package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
class HomeMetricPublishTest {
 @Spy @InjectMocks HomeMetricConfigService service;
 @Mock HomeMetricMapper mapper;
 @Mock HomeMetricVersionMapper versions;
 @Mock HomeMetricDictionarySnapshots snapshots;
 HomeMetricDO row;
 HomeMetricSpec spec(String name){return new HomeMetricSpec("test",name,null,null,1,"test:view",List.of(),List.of(),"MODAL","a",false,0,List.of());}
 @BeforeAll static void metadata(){var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"home-metric-publish-test");com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,HomeMetricDO.class);com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,HomeMetricVersionDO.class);}
 @BeforeEach void setup(){row=new HomeMetricDO();row.setId(1L);row.setVersion(3);row.setPublishedVersion(1);row.setEnabled(true);row.setCode("test");row.setDraftJson(JsonUtils.toJsonString(spec("草稿")));when(mapper.selectById(1L)).thenReturn(row);doNothing().when(service).check(eq(1L),anyString(),eq(7L));}
 void validation(){doNothing().when(service).validate(any(),eq(7L));when(snapshots.capture(any(),any(),eq(7L))).thenAnswer(i->i.getArgument(0));}
 @Test void thirteenthEnabledMetricIsRejected(){
  validation();row.setEnabled(false);var occupied=new HomeMetricDO();occupied.setEnabled(true);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.nCopies(12,occupied));
  var error=assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.publish(new HomeMetricConfigService.Command(1L,3,null),7L));
  assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_ENABLED_LIMIT.getCode(),error.getCode());verifyNoInteractions(versions);
 }
 @Test void stalePublishNeverWritesSnapshot(){validation();assertThrows(RuntimeException.class,()->service.publish(new HomeMetricConfigService.Command(1L,2,null),7L));verifyNoInteractions(versions);verify(mapper,never()).update(any(),any());}
 @Test void racingPublishNeverWritesSuccessSnapshot(){validation();when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0);assertThrows(RuntimeException.class,()->service.publish(new HomeMetricConfigService.Command(1L,3,null),7L));verifyNoInteractions(versions);}
 @Test void publishCreatesNewImmutableVersion(){validation();when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);service.publish(new HomeMetricConfigService.Command(1L,3,null),7L);var capture=ArgumentCaptor.forClass(HomeMetricVersionDO.class);verify(versions).insert(capture.capture());assertEquals(4,capture.getValue().getVersion());assertEquals("草稿",JsonUtils.parseObject(capture.getValue().getSpecJson(),HomeMetricSpec.class).name());verify(versions,never()).update(any(),any());}
 @Test void rollbackCreatesNewVersionInsteadOfEditingHistory(){validation();var old=new HomeMetricVersionDO();old.setMetricId(1L);old.setVersion(1);old.setSpecJson(JsonUtils.toJsonString(spec("旧发布")));when(versions.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(old);when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);service.publish(new HomeMetricConfigService.Command(1L,3,1),7L);var capture=ArgumentCaptor.forClass(HomeMetricVersionDO.class);verify(versions).insert(capture.capture());assertEquals(4,capture.getValue().getVersion());assertEquals("旧发布",JsonUtils.parseObject(capture.getValue().getSpecJson(),HomeMetricSpec.class).name());assertEquals(1,old.getVersion());verify(versions,never()).update(any(),any());}
 @Test void draftSaveNeverWritesPublishedVersion(){validation();when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);service.save(new HomeMetricConfigService.Save(1L,3,spec("新草稿")),7L);verifyNoInteractions(versions);var wrapper=ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);verify(mapper).update(isNull(),wrapper.capture());assertFalse(wrapper.getValue().getSqlSet().contains("published_version"));assertTrue(wrapper.getValue().getSqlSet().contains("draft_json"));}
}
