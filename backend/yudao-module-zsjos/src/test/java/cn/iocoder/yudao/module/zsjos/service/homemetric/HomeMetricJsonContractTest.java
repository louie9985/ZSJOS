package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HomeMetricJsonContractTest {
 @Test void invalidBoundaryTokensAreNotCoercedIntoEpoch() {
  for(String value:List.of("true","false","0.1","null","{}","[]")) {
   String json="{\"start\":"+value+",\"end\":1793289600000}";
   assertThrows(Exception.class,()->JsonUtils.parseObject(json,MetricPeriod.Range.class));
  }
  assertThrows(IllegalArgumentException.class,()->new MetricPeriod.Range(LocalDateTime.MIN,LocalDateTime.now()));
 }
 @Test void intervalEpochAndRedisRoundTripAreIndependentOfJvmTimezone() throws Exception {
  var previous=TimeZone.getDefault();
  try {
   TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
   var range=new MetricPeriod.Range(LocalDateTime.of(2026,10,29,0,0),LocalDateTime.of(2026,10,30,0,0));
   String json=JsonUtils.toJsonString(range);
   var tree=JsonUtils.getObjectMapper().readTree(json);
   assertEquals(Instant.parse("2026-10-28T16:00:00Z").toEpochMilli(),tree.path("start").longValue());
   assertEquals(range,JsonUtils.parseObject(json,MetricPeriod.Range.class));
  } finally {TimeZone.setDefault(previous);}
 }
 @Test void redisRecordsRoundTripAndPublicProjectionContainsNoPrivateContext() throws Exception {
  var condition=new AdvancedFilterConditionReqVO();condition.setFieldKey("lead.submittedAt");condition.setOperator("relative");condition.setValue("today");condition.setRelativeBaseDate(LocalDate.of(2026,10,29));
  var filter=new AdvancedFilterGroupReqVO();filter.setLogic("AND");filter.setConditions(List.of(condition));filter.setGroups(List.of());
  var range=new MetricPeriod.Range(LocalDateTime.of(2026,10,29,0,0),LocalDateTime.of(2026,10,30,0,0));
  var calculation=new MetricEvaluation.Operand("a","lead",MetricAggregate.COUNT,null,MetricPeriod.Kind.CUSTOM,false,range,MetricPeriod.Comparison.CURRENT);
  var spec=new HomeMetricSpec("test","测试指标",null,null,1,"test:view",List.of(7L),List.of(20L),"MODAL","a",false,0,List.of(new HomeMetricSpec.Operand(calculation,filter,"lead.submittedAt"))).withSnapshots(Map.of("a:kind:kind:x","历史标签"));
  var at=Instant.parse("2026-10-29T03:00:00Z");
  var evaluation=new HomeMetricConfigService.Evaluation(at,new HomeMetricConfigService.Value("OK","9007199254740993"),MetricUnit.of("COUNT"),List.of(new HomeMetricConfigService.Input("a","客资",range,new HomeMetricConfigService.Value("OK","9007199254740993"))));
  var context=new HomeMetricConfigService.Context(1L,2,7L,1L,spec,evaluation,at.plusSeconds(900));
  String json=JsonUtils.toJsonString(context);var decoded=JsonUtils.parseObject(json,HomeMetricConfigService.Context.class);
  assertEquals(context,decoded);assertEquals(LocalDate.of(2026,10,29),decoded.spec().operands().getFirst().filter().getConditions().getFirst().getRelativeBaseDate());
  var tree=JsonUtils.getObjectMapper().readTree(json);
  assertTrue(tree.path("evaluation").path("result").path("value").isString());
  assertTrue(tree.path("evaluation").path("inputs").get(0).path("range").path("start").isNumber());
  assertEquals(range.start(),decoded.evaluation().inputs().getFirst().range().start());assertEquals(at,decoded.evaluation().evaluatedAt());
  var service=new HomeMetricConfigService();var method=HomeMetricConfigService.class.getDeclaredMethod("publicSpec",HomeMetricSpec.class);method.setAccessible(true);
  var publicSpec=(HomeMetricSpec)method.invoke(service,spec);
  var view=new HomeMetricConfigService.ContextView(1L,2,publicSpec,evaluation,context.expiresAt());
  String publicJson=JsonUtils.toJsonString(view);var publicTree=JsonUtils.getObjectMapper().readTree(publicJson);
  assertFalse(publicTree.has("actor"));assertFalse(publicTree.has("tenant"));assertFalse(publicTree.path("spec").has("viewPermission"));assertFalse(publicTree.path("spec").path("operands").get(0).has("filter"));assertTrue(publicTree.path("spec").path("userIds").isEmpty());assertTrue(publicTree.path("spec").path("dictionarySnapshots").isEmpty());
  assertFalse(publicJson.contains("历史标签"));assertFalse(publicJson.contains("relativeBaseDate"));
  System.out.println("HomeMetric JSON date shapes: range.start="+tree.path("evaluation").path("inputs").get(0).path("range").path("start").getNodeType()+", evaluatedAt="+tree.path("evaluation").path("evaluatedAt").getNodeType()+", relativeBaseDate="+tree.path("spec").path("operands").get(0).path("filter").path("conditions").get(0).path("relativeBaseDate").getNodeType());
 }
}
