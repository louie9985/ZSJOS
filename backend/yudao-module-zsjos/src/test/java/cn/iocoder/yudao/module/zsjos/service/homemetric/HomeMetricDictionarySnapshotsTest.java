package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class HomeMetricDictionarySnapshotsTest {
 @InjectMocks HomeMetricDictionarySnapshots service;
 @Mock DictDataApi dictionaries;
 @Mock AdvancedFilterService filters;
 @Mock HomeMetricSources sources;
 HomeMetricSpec spec;
 @BeforeEach void setup(){
  var field=mock(AdvancedFilterCatalogRespVO.FieldVO.class);when(field.fieldKey()).thenReturn("kind");when(field.declaredOptionSource()).thenReturn("dict:kind");
  when(filters.catalog("lead")).thenReturn(new AdvancedFilterCatalogRespVO(List.of(field),List.of()));
  when(sources.catalog(7L)).thenReturn(List.of(new HomeMetricSources.Source("lead","客资","lead",List.of(),List.of(),"/lead")));
  var entry=new DictDataRespDTO();entry.setValue("x");entry.setLabel("新名称");entry.setStatus(0);when(dictionaries.getDictDataList("kind")).thenReturn(List.of(entry));
  var condition=new AdvancedFilterConditionReqVO();condition.setFieldKey("kind");condition.setOperator("eq");condition.setValue("x");
  var filter=new AdvancedFilterGroupReqVO();filter.setLogic("AND");filter.setConditions(List.of(condition));filter.setGroups(List.of());
  var calculation=new MetricEvaluation.Operand("a","lead",MetricAggregate.COUNT,null,MetricPeriod.Kind.ALL,false,null,MetricPeriod.Comparison.CURRENT);
  spec=new HomeMetricSpec("test","测试",null,null,1,"test:view",List.of(),List.of(),"MODAL","a",false,0,List.of(new HomeMetricSpec.Operand(calculation,filter,null)));
 }
 @Test void capturesServerLabelAndIgnoresForgedClientSnapshot(){
  var result=service.capture(spec.withSnapshots(Map.of("a:kind:kind:x","伪造")),null,7L);
  assertEquals("新名称",result.dictionarySnapshots().get("a:kind:kind:x"));
  verify(dictionaries).validateDictDataList("kind",List.of("x"));
  when(sources.catalog(7L)).thenReturn(List.of());
  assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_DENIED.getCode(),
    assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.capture(spec,null,7L)).getCode());
 }
 @Test void unchangedSelectionRetainsHistoricalLabel(){
  var result=service.capture(spec,spec.withSnapshots(Map.of("a:kind:kind:x","历史名称")),7L);
  assertEquals("历史名称",result.dictionarySnapshots().get("a:kind:kind:x"));
  when(dictionaries.getDictDataList("kind")).thenReturn(List.of());
  assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_INVALID.getCode(),
    assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.capture(spec,null,7L)).getCode());
 }
}
