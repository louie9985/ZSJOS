package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.Query;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.PerformanceFact;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceEnabledContributionTest {
 @InjectMocks PerformanceStatisticsService service;
 @Mock PerformanceAccess access;
 @Mock PerformanceFactMapper facts;
 @Mock PerformanceTargetService targets;
 @Mock PerformanceReportMapper reports;
 @Mock PerformanceDetailMapper detailQueries;
 PerformanceFact order(long user,String amount){var f=new PerformanceFact();f.setUserId(user);f.setUserName("销售"+user);f.setDeptId(10L);f.setAmount(new BigDecimal(amount));f.setOccurredAt(LocalDate.of(2026,1,2).atStartOfDay());return f;}
 @Test void historicalDisabledOrdersRemainInTotalsButNotContributionRows(){
  TenantContextHolder.setTenantId(1L);
  try {
   var q=new Query();q.setScopeType("DEPT");q.setScopeId(10L);q.setStart(LocalDate.of(2026,1,2));q.setEnd(LocalDate.of(2026,1,3));q.setGrain("day");
   when(detailQueries.conversionOrders(any())).thenReturn(List.of(order(2,"100"),order(3,"300")));
   when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(true,false,Set.of()));
   var enabled=new AdminUserRespDTO();enabled.setId(2L);enabled.setStatus(0);enabled.setNickname("销售2");
   var disabled=new AdminUserRespDTO();disabled.setId(3L);disabled.setStatus(1);disabled.setNickname("销售3");
   when(access.users(Set.of(2L,3L))).thenReturn(List.of(enabled,disabled));
   when(reports.amounts(any())).thenAnswer(call->{var request=call.getArgument(0,PerformanceReportQuery.class);var result=new ArrayList<PerformanceAggregate>();
    for(var w:request.getIntervals())switch(request.getGrouping()){
     case "total"->result.add(PerformanceReportTest.sum(w.key(),"total","400",2,"400",2));
     case "user","contributor"->{for(long id:List.of(2L,3L)){var a=PerformanceReportTest.sum(w.key(),id+("user".equals(request.getGrouping())?"":"|销售"+id),id==2?"100":"300",1,id==2?"100":"300",1);a.setUserId(id);result.add(a);}}
    }
    return result;
   });
   var result=service.analysis(q);
   assertEquals(new BigDecimal("400"),result.averages().getFirst().amount());
   assertEquals(2,result.averages().getFirst().orders());
   assertEquals(1,result.contributionMetrics().size());
   assertEquals(2L,result.contributionMetrics().getFirst().userId());
   assertEquals(new BigDecimal("0.250000"),result.contributionMetrics().getFirst().share());
   assertEquals(1,result.contributors().size());
   assertEquals(new BigDecimal("0.250000"),result.contributors().getFirst().share());
  } finally {TenantContextHolder.clear();}
 }
}
