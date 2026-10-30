package cn.iocoder.yudao.module.zsjos.service.homemetric.source;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MetricRelativeFilterTest {
 @Test void frozenDateDrivesCompiledSqlAndInMemoryHalfOpenMatching(){
  cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
  try {
   var group=new AdvancedFilterGroupReqVO();var condition=new AdvancedFilterConditionReqVO();
   condition.setFieldKey("lead.submittedAt");condition.setOperator("relative");condition.setValue("today");group.setConditions(List.of(condition));
   var service=new AdvancedFilterService();var frozen=service.freezeRelativeDates(group,Instant.parse("2026-10-28T15:59:59Z"));
   var query=service.buildLeadQuery(frozen);
   assertTrue(query.getParameters().containsValue(LocalDateTime.parse("2026-10-28T00:00:00")));
   assertTrue(query.getParameters().containsValue(LocalDateTime.parse("2026-10-29T00:00:00")));
   assertTrue(service.matches("lead",frozen,key->LocalDateTime.parse("2026-10-28T00:00:00")));
   assertFalse(service.matches("lead",frozen,key->LocalDateTime.parse("2026-10-29T00:00:00")));
  } finally {cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
 }
 @Test void freezesOrTreeWithoutAddingGroupsOrMutatingOriginal(){
  var root=new AdvancedFilterGroupReqVO();root.setLogic("OR");
  for(int i=0;i<5;i++){var group=new AdvancedFilterGroupReqVO();var condition=new AdvancedFilterConditionReqVO();condition.setFieldKey("lead.submittedAt");condition.setOperator("relative");condition.setValue("today");group.setConditions(List.of(condition));root.getGroups().add(group);}
  var service=new AdvancedFilterService();var frozen=service.freezeRelativeDates(root,Instant.parse("2026-10-28T15:59:59Z"));
  assertEquals("OR",frozen.getLogic());assertEquals(5,frozen.getGroups().size());
  for(var group:frozen.getGroups()){assertEquals(1,group.getConditions().size());assertEquals(LocalDate.of(2026,10,28),group.getConditions().getFirst().getRelativeBaseDate());}
  assertNull(root.getGroups().getFirst().getConditions().getFirst().getRelativeBaseDate());
  var next=service.freezeRelativeDates(frozen,Instant.parse("2026-10-28T16:00:00Z"));
  assertEquals(LocalDate.of(2026,10,29),next.getGroups().getFirst().getConditions().getFirst().getRelativeBaseDate());
  assertEquals(LocalDate.of(2026,10,28),frozen.getGroups().getFirst().getConditions().getFirst().getRelativeBaseDate());
 }
}
