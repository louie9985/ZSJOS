package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceConversionAmountTest {
 private PerformanceFact receipt(long id, LocalDateTime at) {
  var f=new PerformanceFact();f.setLeadId(id);f.setReceivedAt(at);f.setStatus("valid");return f;
 }
 private PerformanceFact order(long id,String amount,LocalDateTime received,LocalDateTime submitted) {
  var f=receipt(id,received);f.setAmount(amount==null?null:new BigDecimal(amount));f.setOccurredAt(submitted);f.setOrderType("first_purchase");return f;
 }
 @Test void thresholdIsInclusivePerOrderAcrossAllSixWindows() {
  var now=LocalDateTime.of(2026,10,8,12,0);
  for(String key:List.of("month","lastMonth","last7","last30","last60","last90")) {
   var w=PerformancePeriods.window(key,now);var at=w.start().plusHours(1);var submitted=at.plusHours(1);
   var orders=List.of(order(1,"1279.99",at,submitted),order(2,"1280.00",at,submitted),
    order(3,"1280.01",at,submitted),order(4,"640",at,submitted),order(4,"640",at,submitted.plusMinutes(1)),
    order(5,null,at,submitted),order(6,"100",at,submitted),order(6,"1280",at,submitted.plusMinutes(1)),
    order(6,"1500",at,submitted.plusMinutes(2)));
   var receipts=new ArrayList<PerformanceFact>();for(long id=1;id<=6;id++)receipts.add(receipt(id,at));
   var pop=PerformanceConversion.population(w,orders,receipts);
   assertEquals(6,pop.size(),key);
   assertEquals(Set.of(2L,3L,6L),pop.entrySet().stream().filter(e->e.getValue().order()!=null).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()),key);
   assertSame(orders.get(7),pop.get(6L).order());
   var m=PerformanceStatisticsService.metric(w,orders,receipts);
   assertEquals(3,m.converted());assertEquals(6,m.denominator());assertEquals(new BigDecimal("0.500000"),m.rate());
   assertEquals(9,m.orders());assertEquals(new BigDecimal("8000.00"),m.amount());
   assertEquals(8,m.averageOrders());assertEquals(new BigDecimal("8000.00"),m.averageAmount());
  }
 }
 @Test void olderLeadsOnlyEnterMonthWhenIndividualOrderQualifies() {
  var now=LocalDateTime.of(2026,10,8,12,0);var old=LocalDateTime.of(2026,9,25,10,0);var submitted=now.minusDays(1);
  var small=order(1,"1279.99",old,submitted);var enough=order(2,"1280",old,submitted);
  var w=PerformancePeriods.window("month",now);
  assertEquals(Set.of(2L),PerformanceConversion.population(w,List.of(small,enough),List.of()).keySet());
  var empty=PerformanceStatisticsService.metric(w,List.of(small),List.of());
  assertEquals(0,empty.converted());assertEquals(0,empty.denominator());assertNull(empty.rate());
  enough.setOrderType("repurchase");
  assertTrue(PerformanceConversion.population(w,List.of(enough),List.of()).isEmpty());
 }
}
