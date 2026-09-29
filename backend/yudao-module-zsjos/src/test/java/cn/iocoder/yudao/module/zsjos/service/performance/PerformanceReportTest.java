package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PerformanceReportTest {
 @InjectMocks PerformanceStatisticsService service;
 @Mock PerformanceAccess access;
 @Mock PerformanceFactMapper facts;
 @Mock PerformanceTargetService targets;
 Query query;
 @BeforeEach void setup(){
  TenantContextHolder.setTenantId(991L);query=new Query();query.setScopeType("USER");query.setScopeId(1L);
  lenient().when(access.historicalRowAllowed(any(),any())).thenReturn(true);
  lenient().when(access.has("zsjos:sales-performance:detail")).thenReturn(true);
 }
 @AfterEach void clear(){TenantContextHolder.clear();}
 PerformanceFact order(long id,String source,String type,String amount){
  var f=new PerformanceFact();f.setId(id);f.setGroupKey(source);f.setOrderType(type);f.setAmount(new BigDecimal(amount));f.setOccurredAt(LocalDateTime.now(PerformancePeriods.ZONE).minusMinutes(1));return f;
 }
 @Test void nonInboundAveragesSourcesAndDetailsIncludeAllRepurchases(){
  when(facts.orders(991L,"USER",1L)).thenReturn(List.of(order(1,"inbound","first_purchase","100"),order(2,"inbound","repurchase","300"),order(3,"self","first_purchase","0.01"),order(4,null,"first_purchase","50")));
  query.setPeriodKey("today");var analysis=service.analysis(query);
  assertEquals(3,analysis.averages().size());var nonInbound=analysis.averages().get(2);
  assertEquals(new BigDecimal("300"),nonInbound.averageAmount());assertEquals(1,nonInbound.averageOrders());assertEquals(new BigDecimal("300.000000"),nonInbound.average());
  assertEquals(new BigDecimal("300.01"),analysis.sources().stream().filter(g->g.key().equals("self|非引流")).findFirst().orElseThrow().amount());
  assertTrue(analysis.sources().stream().anyMatch(g->g.label().equals("其他")));
  query.setMetric("orders");query.setDimension("source");query.setGroupKey("self|非引流");var details=service.details(query);
  assertEquals(2L,details.getTotal());assertTrue(details.getList().stream().allMatch(x->x.label().equals("非引流")));
 }
 PerformanceFact task(long id,String status,LocalDateTime due){
  var f=new PerformanceFact();f.setId(id);f.setLeadId(200L);f.setNumber("KZ-TEST");f.setOwnerUserId(2L);f.setUserId(1L);f.setGroupKey("lead_follow_up_reminder");f.setStatus(status);f.setDueAt(due);f.setCategory("分类快照");f.setStage("阶段快照");return f;
 }
 @Test void pendingTasksKeepOwnerAssigneeDueAndOverlapAndExcludeUnrelatedScope(){
  var now=LocalDateTime.now(PerformancePeriods.ZONE);var due=now.toLocalDate().atStartOfDay();var task=task(1,"pending",due);var outside=task(2,"pending",due);outside.setDeptId(99L);
  when(facts.tasks(991L,"USER",1L)).thenReturn(List.of(task,outside));when(access.historicalRowAllowed(query,99L)).thenReturn(false);
  var owner=new AdminUserRespDTO();owner.setNickname("归属人");var assignee=new AdminUserRespDTO();assignee.setNickname("执行人");when(access.user(2L)).thenReturn(owner);when(access.user(1L)).thenReturn(assignee);
  query.setMetric("todayFollowUp");var today=service.details(query);query.setMetric("overdueFollowUp");var overdue=service.details(query);
  assertEquals(1L,today.getTotal());assertEquals(1L,overdue.getTotal());var row=today.getList().getFirst();
  assertEquals("归属人",row.ownerName());assertEquals("执行人",row.assigneeName());assertEquals(due,row.dueAt());assertEquals("分类快照",row.category());assertEquals("阶段快照",row.stage());assertEquals(200L,row.leadId());assertNotNull(row.overdueMinutes());
 }
 @Test void followUpStatesAreExclusiveAndReconcile(){
  var day=LocalDate.now(PerformancePeriods.ZONE).minusDays(2);query.setStart(day);query.setEnd(day.plusDays(4));
  when(facts.tasks(991L,"USER",1L)).thenReturn(List.of(task(1,"completed",day.atTime(12,0)),task(2,"pending",day.atTime(12,0)),task(3,"pending",day.plusDays(1).atTime(12,0)),task(4,"cancelled",day.atTime(12,0))));
  var report=service.leads(query);assertEquals(4,report.followUp().stream().mapToLong(Group::count).sum());
  assertEquals(2,report.followUp().stream().filter(g->g.key().equals("overdue")).findFirst().orElseThrow().count());
 }
 @Test void laterTodayPlansRemainPendingAndAppearInDetails(){
  var today=LocalDate.now(PerformancePeriods.ZONE);query.setPeriodKey("today");query.setMetric("tasks");
  when(facts.tasks(991L,"USER",1L)).thenReturn(List.of(task(1,"pending",today.atTime(23,59,59))));
  var report=service.leads(query);
  assertEquals(1,report.followUp().stream().filter(g->g.key().equals("pending")).findFirst().orElseThrow().count());
  assertEquals(1L,service.details(query).getTotal());
 }
 @Test void automaticTasksAreExcludedFromSummaryAndDetailsInEveryScope(){
  var day=LocalDate.now(PerformancePeriods.ZONE).minusDays(1);query.setStart(day);query.setEnd(day.plusDays(1));query.setMetric("tasks");
  var first=task(1,"completed",day.atTime(12,0));first.setGroupKey("lead_first_follow_up");first.setGenerationSource("sales_self_sourced_auto");
  var qualification=task(2,"completed",day.atTime(12,0));qualification.setGroupKey("lead_qualification");qualification.setGenerationSource("sales_self_sourced_auto");
  var reminder=task(3,"pending",day.atTime(12,0));var manual=task(4,"completed",day.atTime(12,0));manual.setGroupKey("lead_first_follow_up");
  for(String scope:List.of("SELF","USER","DEPT","CENTER")){
   query.setScopeType(scope);when(facts.tasks(991L,scope,1L)).thenReturn(List.of(first,qualification,reminder,manual));
   assertEquals(2,service.leads(query).followUp().stream().mapToLong(Group::count).sum(),scope);
   assertEquals(2L,service.details(query).getTotal(),scope);
  }
 }
 @Test void futureHistoryHasNoInventedZeroOrComparison(){
  query.setYear(LocalDate.now(PerformancePeriods.ZONE).getYear()+1);var history=service.history(query);
  assertEquals(12,history.size());assertTrue(history.stream().allMatch(x->x.future()&&x.amount()==null&&x.previousAmount()==null));
 }
 @Test void deniedDetailsAndMissingTargetsReadNoFacts(){
  when(access.has("zsjos:sales-performance:detail")).thenReturn(false);assertThrows(RuntimeException.class,()->service.details(query));
  assertThrows(RuntimeException.class,()->service.missingTargets(query));verifyNoInteractions(facts,targets);
 }

 @Test void conversionDetailsExcludeUnlinkedSelfWhileOrderDetailsKeepThem() {
  var received=LocalDateTime.now(PerformancePeriods.ZONE).minusHours(3);
  var excluded=order(1,"self","first_purchase","100");excluded.setLeadId(1L);excluded.setReceivedAt(received);excluded.setSourceType("sales_self_sourced");
  var included=order(2,"self","first_purchase","200");included.setLeadId(2L);included.setReceivedAt(received);included.setSourceType("sales_self_sourced");included.setSourceProviderUserId(99L);
  var unconverted=new PerformanceFact();unconverted.setLeadId(3L);unconverted.setReceivedAt(received);unconverted.setStatus("valid");unconverted.setSourceType("sales_self_sourced");
  when(facts.orders(991L,"USER",1L)).thenReturn(List.of(excluded,included));
  when(facts.receipts(991L,"USER",1L)).thenReturn(List.of(unconverted));
  query.setStart(received.toLocalDate());query.setEnd(received.toLocalDate().plusDays(1));query.setMetric("conversion");
  var details=service.details(query);assertEquals(1L,details.getTotal());assertEquals(2L,details.getList().getFirst().id());
  query.setMetric("orders");assertEquals(2L,service.details(query).getTotal());
 }
}
