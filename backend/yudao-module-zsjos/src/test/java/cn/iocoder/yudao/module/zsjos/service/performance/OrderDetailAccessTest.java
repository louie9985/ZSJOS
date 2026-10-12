package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.OrderDetailVO;
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
class OrderDetailAccessTest {
    @InjectMocks PerformanceStatisticsService service;
    @Mock PerformanceAccess access;
    @Mock OrderDetailsService orders;
    @Mock PerformanceDetailMapper details;
    @BeforeEach void tenant(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void deniedFeatureAndScopeDoNotReadFacts(){
        assertThrows(RuntimeException.class,()->service.orderDetails(new OrderDetailVO.Query()));verifyNoInteractions(orders,details);
        when(access.has("zsjos:sales-performance:detail")).thenReturn(true);
        doThrow(PerformanceAccess.denied()).when(access).authorize(any(),eq(false));
        assertThrows(RuntimeException.class,()->service.orderDetails(new OrderDetailVO.Query()));verifyNoInteractions(orders,details);
    }
    @Test void tenPeriodsRetainAuthorizedScopeAndExactWindow(){
        var instant=Instant.parse("2026-10-10T04:00:00Z");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"clock",Clock.fixed(instant,PerformancePeriods.ZONE));
        when(access.has("zsjos:sales-performance:detail")).thenReturn(true);
        when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(false,false,Set.of(12L)));
        for(String period:List.of("today","week","month","lastMonth","quarter","year","last7","last30","last60","last90")) {
            var q=new OrderDetailVO.Query();q.setScopeType("USER");q.setScopeId(5L);q.setPeriodKey(period);q.setProductKey("untrusted");service.orderDetails(q);
            var captor=ArgumentCaptor.forClass(PerformanceReportQuery.class);
            verify(orders).read(eq(q),captor.capture(),eq(PerformancePeriods.window(period,LocalDateTime.of(2026,10,10,12,0))),any());
            assertEquals(1L,captor.getValue().getTenant());assertEquals(Set.of(12L),captor.getValue().getDepartments());assertFalse(captor.getValue().isAllDepartments());
            assertEquals(5L,captor.getValue().getId());verify(access).authorize(q,false);
        }
    }
}
