package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceCalculationTest {
 private PerformanceFact order(long lead,String amount,LocalDateTime received,LocalDateTime submitted,String type){var f=new PerformanceFact();f.setLeadId(lead);f.setAmount(new BigDecimal(amount));f.setReceivedAt(received);f.setOccurredAt(submitted);f.setOrderType(type);return f;}
 private PerformanceFact receipt(long lead,LocalDateTime at){var f=new PerformanceFact();f.setLeadId(lead);f.setReceivedAt(at);f.setStatus("valid");return f;}
 @Test void windowBoundaries(){var n=LocalDateTime.of(2026,1,1,12,0);assertEquals(LocalDate.of(2025,12,29),PerformancePeriods.window("week",n).start().toLocalDate());assertEquals(n.toLocalDate().minusDays(6),PerformancePeriods.window("last7",n).start().toLocalDate());assertEquals(LocalDate.of(2025,12,1),PerformancePeriods.window("lastMonth",n).start().toLocalDate());assertEquals(LocalDate.of(2024,2,1),PerformancePeriods.window("month",LocalDateTime.of(2024,2,29,12,0)).start().toLocalDate());}
 @Test void sixtyDaysIsExclusiveAndTransferRestarts(){var at=LocalDateTime.of(2026,1,1,10,0);assertTrue(PerformancePeriods.withinValidity(at,at.plusDays(60).minusNanos(1)));assertFalse(PerformancePeriods.withinValidity(at,at.plusDays(60)));assertTrue(PerformancePeriods.withinValidity(at.plusDays(30),at.plusDays(70)));assertFalse(PerformancePeriods.withinValidity(at,at.minusSeconds(1)));}
 @Test void teamDedupOldConversionsAndRepurchase(){var n=LocalDateTime.of(2026,9,22,12,0);var fresh=n.minusDays(5);var old=n.minusDays(30);var w=PerformancePeriods.window("month",n);var metric=PerformanceStatisticsService.metric(w,List.of(order(1,"100",fresh,n.minusDays(1),"first_purchase"),order(1,"200",fresh,n.minusDays(1),"repurchase"),order(2,"100",old,n.minusDays(1),"first_purchase")),List.of(receipt(1,fresh),receipt(1,fresh.plusDays(1)),receipt(3,fresh)));assertEquals(2,metric.converted());assertEquals(3,metric.denominator());assertEquals(3,metric.orders());assertEquals(new BigDecimal("400"),metric.amount());assertEquals(new BigDecimal("0.666667"),metric.rate());}
 @Test void averageExcludesBothNumeratorAndDenominator(){var n=LocalDateTime.of(2026,9,22,12,0);var rows=List.of(order(1,"0",n.minusDays(1),n.minusHours(1),"first_purchase"),order(2,"0.01",n.minusDays(1),n.minusHours(1),"first_purchase"),order(3,"100",n.minusDays(1),n.minusHours(1),"first_purchase"));var m=PerformanceStatisticsService.metric(PerformancePeriods.window("month",n),rows,List.of());assertEquals(3,m.orders());assertEquals(new BigDecimal("100.01"),m.amount());assertEquals(new BigDecimal("100.000000"),m.average());}
 @Test void emptyIsNotZeroConversion(){var m=PerformanceStatisticsService.metric(PerformancePeriods.window("month",LocalDateTime.now()),List.of(),List.of());assertNull(m.rate());assertNull(m.average());assertEquals(BigDecimal.ZERO,m.amount());}
 @Test void rollingNinetyIncludesLateConversionButMonthDoesNot(){
  var now=LocalDateTime.of(2026,9,28,12,0);var received=now.minusDays(80);
  var orders=List.of(order(1,"100",received,now.minusDays(1),"first_purchase"));var receipts=List.of(receipt(1,received));
  var rolling=PerformanceStatisticsService.metric(PerformancePeriods.window("last90",now),orders,receipts);
  assertEquals(1,rolling.converted());assertEquals(1,rolling.denominator());
  var month=PerformanceStatisticsService.metric(PerformancePeriods.window("month",now),orders,receipts);
  assertEquals(0,month.converted());assertEquals(0,month.denominator());
 }
 @Test void rollingNeverImportsEarlierReceiptsAndRequiresMatchingSellerReceipt(){
  var now=LocalDateTime.of(2026,9,28,12,0);var old=now.minusDays(10);var fresh=now.minusDays(2);
  var converted=order(1,"100",old,now.minusDays(1),"first_purchase");
  var unrelated=receipt(1,fresh);
  var m=PerformanceStatisticsService.metric(PerformancePeriods.window("last7",now),List.of(converted),List.of(unrelated));
  assertEquals(0,m.converted());assertEquals(1,m.denominator());
  converted.setReceivedAt(fresh);converted.setUserId(2L);unrelated.setUserId(1L);
  assertEquals(0,PerformanceStatisticsService.metric(PerformancePeriods.window("last7",now),List.of(converted),List.of(unrelated)).converted());
 }
 @Test void monthlyAndRollingDrilldownPopulationsReconcileAndDeduplicate(){
  var now=LocalDateTime.of(2026,9,28,12,0);var fresh=now.minusDays(3);var old=now.minusDays(30);
  var rows=List.of(order(1,"100",fresh,now.minusDays(1),"first_purchase"),order(1,"200",fresh,now.minusHours(1),"first_purchase"),order(2,"300",old,now.minusDays(1),"first_purchase"),order(3,"100",fresh,now.minusDays(1),"repurchase"));
  var receipts=List.of(receipt(1,fresh),receipt(2,old),receipt(3,fresh));
  for(String key:List.of("month","last7","last30","last60","last90")){
   var w=PerformancePeriods.window(key,now);var population=PerformanceConversion.population(w,rows,receipts);var metric=PerformanceStatisticsService.metric(w,rows,receipts);
   assertEquals(population.size(),metric.denominator());assertEquals(population.values().stream().filter(r->r.order()!=null).count(),metric.converted());
  }
  assertEquals(1,PerformanceStatisticsService.metric(PerformancePeriods.window("last7",now),rows,receipts).converted());
 }
 @Test void averageExposesExactInputsAndRepurchaseIsNonInbound(){
  var now=LocalDateTime.of(2026,9,28,12,0);var buy=order(1,"100",now.minusDays(2),now.minusDays(1),"repurchase");buy.setGroupKey("inbound");
  assertEquals("self",PerformanceStatisticsService.sourceGroup(buy));
  var metric=PerformanceStatisticsService.metric(PerformancePeriods.window("month",now),List.of(buy,order(2,"0.01",now.minusDays(2),now.minusDays(1),"first_purchase")),List.of());
  assertEquals(new BigDecimal("100"),metric.averageAmount());assertEquals(1,metric.averageOrders());assertEquals(2,metric.orders());
 }
 @Test void expiredAndUnrelatedTransfersDoNotConvert(){var n=LocalDateTime.of(2026,9,22,12,0);var m=PerformanceStatisticsService.metric(PerformancePeriods.window("month",n),List.of(order(1,"100",n.minusDays(80),n.minusDays(1),"first_purchase")),List.of(receipt(1,n.minusDays(2))));assertEquals(0,m.converted());assertEquals(1,m.denominator());}

 private PerformanceFact sourced(PerformanceFact fact, String source, Long provider) {
  fact.setSourceType(source); fact.setSourceProviderUserId(provider); return fact;
 }
 @Test void allSixConversionPeriodsExcludeUnlinkedSelfSourcedButKeepMoney() {
  var now=LocalDateTime.of(2026,9,28,12,0);
  for(String key:List.of("month","lastMonth","last7","last30","last60","last90")) {
   var w=PerformancePeriods.window(key,now);var received=w.start().plusHours(1);var submitted=received.plusHours(1);
   var rows=new ArrayList<PerformanceFact>();var receipts=new ArrayList<PerformanceFact>();
   String[] sources={"sales_self_sourced","sales_self_sourced","internal_new_media","partner",null};
   for(int i=0;i<sources.length;i++) {
    Long provider=i==1?99L:null;
    rows.add(sourced(order(i+1,"100",received,submitted,"first_purchase"),sources[i],provider));
    receipts.add(sourced(receipt(i+1,received),sources[i],provider));
   }
   receipts.add(sourced(receipt(6,received),"sales_self_sourced",null));
   // A coarse financial source group is not evidence of explicit provider linkage.
   rows.getFirst().setGroupKey("inbound");rows.get(1).setGroupKey("self");
   var m=PerformanceStatisticsService.metric(w,rows,receipts);
   assertEquals(4,m.converted(),key);assertEquals(4,m.denominator(),key);
   assertEquals(new BigDecimal("1.000000"),m.rate(),key);
   assertEquals(new BigDecimal("500"),m.amount(),key);assertEquals(5,m.orders(),key);
   assertEquals(new BigDecimal("500"),m.averageAmount(),key);assertEquals(5,m.averageOrders(),key);
   assertEquals(new BigDecimal("100.000000"),m.average(),key);
   var population=PerformanceConversion.population(w,rows,receipts);
   assertEquals(Set.of(2L,3L,4L,5L),population.keySet(),key);
  }
 }
 @Test void excludedOrdersCannotReenterMonthlyDenominatorWithoutReceiptSnapshots() {
  var now=LocalDateTime.of(2026,9,28,12,0);var submitted=now.minusDays(1);
  var old=sourced(order(1,"100",now.minusDays(35),submitted,"first_purchase"),"sales_self_sourced",null);
  var fresh=sourced(order(2,"200",now.minusDays(2),submitted,"first_purchase"),"sales_self_sourced",null);
  var m=PerformanceStatisticsService.metric(PerformancePeriods.window("month",now),List.of(old,fresh),List.of());
  assertEquals(0,m.denominator());assertEquals(0,m.converted());assertNull(m.rate());
  assertEquals(new BigDecimal("300"),m.amount());assertEquals(2,m.orders());
  old.setSourceProviderUserId(99L);
  var restored=PerformanceStatisticsService.metric(PerformancePeriods.window("month",now),List.of(old,fresh),List.of());
  assertEquals(1,restored.denominator());assertEquals(1,restored.converted());
 }
}
