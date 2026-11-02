package cn.iocoder.yudao.module.zsjos.service.advancedfilter;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import static org.junit.jupiter.api.Assertions.*;
class RelativeTimeOperandTest {
 final LocalDateTime now=LocalDateTime.of(2026,11,1,12,34);
 Map<String,Object> expr(String base,int offset,String unit) {return Map.of("kind","relative","base",base,"offset",offset,"unit",unit);}
 AdvancedFilterGroupReqVO group(String op,Object value) {var c=new AdvancedFilterConditionReqVO();c.setFieldKey("lead.qualificationDeadlineAt");c.setOperator(op);c.setValue(value);var g=new AdvancedFilterGroupReqVO();g.setConditions(List.of(c));return g;}
 @Test void calendarBoundariesAndOffsets() {
  assertEquals(LocalDateTime.of(2026,11,1,0,0),RelativeTimeOperand.resolve(expr("today_start",0,"day"),now));
  assertEquals(LocalDateTime.of(2026,10,26,0,0),RelativeTimeOperand.resolve(expr("week_start",0,"day"),now));
  assertEquals(LocalDateTime.of(2026,10,1,0,0),RelativeTimeOperand.resolve(expr("quarter_start",0,"day"),now));
  assertEquals(now.minusHours(3),RelativeTimeOperand.resolve(expr("now",-3,"hour"),now));
  assertEquals(LocalDateTime.of(2024,2,29,12,0),RelativeTimeOperand.resolve(expr("now",1,"month"),LocalDateTime.of(2024,1,31,12,0)));
 }
 @Test void invalidExpressionsFailClosed() {
  for(var v:List.of(expr("bad",0,"day"),expr("now",10001,"day"),expr("now",0,"bad"),Map.of("kind","relative"))) assertThrows(RuntimeException.class,()->RelativeTimeOperand.resolve(v,now));
  var v=new HashMap<>(expr("now",0,"day"));v.put("offset",1.5);assertThrows(RuntimeException.class,()->RelativeTimeOperand.resolve(v,now));
 }
 @Test void comparisonSqlAndMemoryAgreeAndTemplatesStayDynamic() {
  var service=new AdvancedFilterService();
  for(String op:List.of("eq","gt","gte","lt","lte")) {
   var original=group(op,expr("today_start",0,"day"));
   var frozen=service.freezeRelativeDates(original,now.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
   var query=service.buildLeadQuery(frozen);
   assertTrue(query.getParameters().containsValue(now.toLocalDate().atStartOfDay()));
   assertEquals(Set.of("eq","gte","lte").contains(op),service.matches("lead",frozen,k->now.toLocalDate().atStartOfDay()));
   assertFalse(service.matches("lead",frozen,k->null));
   assertInstanceOf(Map.class,original.getConditions().getFirst().getValue());
  }
 }
 @Test void mixedIntervalAndFrozenNow() {
  var service=new AdvancedFilterService();var g=group("between",null);var c=g.getConditions().getFirst();
  c.setValueFrom(expr("now",-3,"hour"));c.setValueTo(expr("now",0,"hour"));
  var frozen=service.freezeRelativeDates(g,now.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
  assertTrue(service.matches("lead",frozen,k->now));assertFalse(service.matches("lead",frozen,k->now.plusSeconds(1)));
  assertEquals(now, frozen.getConditions().getFirst().getValueTo());
  c.setValueFrom(expr("now",1,"hour"));assertThrows(RuntimeException.class,()->service.buildLeadQuery(g));
 }
}
