package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.Query;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PerformanceDetailServiceTest {
 @InjectMocks PerformanceStatisticsService service;
 @Mock PerformanceAccess access;
 @Mock PerformanceFactMapper facts;
 @Mock PerformanceDetailMapper detailQueries;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(991L);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 Query query(){var q=new Query();q.setScopeType("USER");q.setScopeId(12L);q.setPageNo(2);q.setPageSize(20);q.setPeriodKey("today");return q;}
 void allowed(){when(access.has("zsjos:sales-performance:detail")).thenReturn(true);when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(false,false,Set.of(10L)));}
 @Test void countAndPageShareScopeAndDoNotMaterializeUnrelatedFacts(){
  allowed();var q=query();var row=new PerformanceFact();row.setId(31L);row.setNumber("ORDER-TEST");row.setOrderType("repurchase");
  when(detailQueries.count(any())).thenReturn(41L);when(detailQueries.page(any())).thenReturn(List.of(row));
  var result=service.details(q);assertEquals(41L,result.getTotal());assertEquals("非引流",result.getList().getFirst().label());
  var count=ArgumentCaptor.forClass(PerformanceDetailQuery.class);var page=ArgumentCaptor.forClass(PerformanceDetailQuery.class);
  verify(detailQueries).count(count.capture());verify(detailQueries).page(page.capture());assertSame(count.getValue(),page.getValue());
  var request=page.getValue();assertEquals(20,request.getOffset());assertEquals(20,request.getSize());assertEquals(991L,request.getTenant());assertEquals(Set.of(10L),request.getDepartments());
  assertFalse(request.isAllDepartments());assertFalse(request.isMissingDepartment());verifyNoInteractions(facts);
 }
 @Test void pastLastPageDoesNotQueryRows(){allowed();when(detailQueries.count(any())).thenReturn(12L);var result=service.details(query());assertTrue(result.getList().isEmpty());assertEquals(12L,result.getTotal());verify(detailQueries,never()).page(any());verifyNoInteractions(facts);}
 @Test void deniedDetailsDoNotReadSql(){assertThrows(RuntimeException.class,()->service.details(query()));verifyNoInteractions(detailQueries,facts);}
 @Test void cumulativeUsesScopedMinimumAndTasksKeepFullDueDay(){
  allowed();var q=query();q.setPeriodKey(null);q.setCumulative(true);q.setMetric("tasks");
  var first=LocalDate.of(2025,1,2).atStartOfDay();when(detailQueries.firstDate(any())).thenReturn(first);
  service.details(q);var arg=ArgumentCaptor.forClass(PerformanceDetailQuery.class);verify(detailQueries).count(arg.capture());
  assertEquals(first,arg.getValue().getStart());assertEquals(LocalDate.now(PerformancePeriods.ZONE).plusDays(1).atStartOfDay(),arg.getValue().getDueEnd());verifyNoInteractions(facts);
 }
}
