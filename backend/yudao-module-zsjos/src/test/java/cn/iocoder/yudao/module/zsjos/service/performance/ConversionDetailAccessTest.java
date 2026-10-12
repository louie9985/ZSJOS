package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.ConversionDetailVO.Query;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConversionDetailAccessTest {
    @InjectMocks PerformanceStatisticsService service;
    @Mock PerformanceAccess access;
    @Mock PerformanceDetailMapper details;
    @Mock ConversionSnapshotMapper snapshots;
    @BeforeEach void tenant(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void missingDetailPermissionStopsAllQueries(){
        assertThrows(RuntimeException.class,()->service.conversionDetails(new Query()));verifyNoInteractions(details,snapshots);
    }
    @Test void rejectedScopeStopsAllQueries(){
        when(access.has("zsjos:sales-performance:detail")).thenReturn(true);
        doThrow(PerformanceAccess.denied()).when(access).authorize(any(),eq(false));
        assertThrows(RuntimeException.class,()->service.conversionDetails(new Query()));verifyNoInteractions(details,snapshots);
    }
    @Test void enrichmentOnlyUsesAuthorizedPopulationAndCurrentTenant(){
        when(access.has("zsjos:sales-performance:detail")).thenReturn(true);
        when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(false,false,Set.of(12L)));
        var time=LocalDateTime.now(PerformancePeriods.ZONE).minusMinutes(1);
        var receipt=new PerformanceFact();receipt.setId(5L);receipt.setLeadId(5L);receipt.setReceivedAt(time);receipt.setStatus("valid");
        when(details.conversionOrders(any())).thenReturn(List.of());when(details.conversionReceipts(any())).thenReturn(List.of(receipt));
        when(snapshots.leads(1L,List.of(5L))).thenReturn(List.of());
        var q=new Query();q.setScopeType("DEPT");q.setScopeId(12L);q.setPeriodKey("last7");q.setProductKey("untrusted-product-key");
        var response=service.conversionDetails(q);assertEquals(0,response.total());assertEquals(1,response.summary().denominator());
        var captor=ArgumentCaptor.forClass(PerformanceDetailQuery.class);verify(details).conversionOrders(captor.capture());
        assertEquals(1L,captor.getValue().getTenant());assertEquals(Set.of(12L),captor.getValue().getDepartments());assertFalse(captor.getValue().isAllDepartments());assertNull(captor.getValue().getDimension());
        verify(access).authorize(q,false);verify(snapshots).leads(1L,List.of(5L));verify(snapshots,never()).products(anyLong(),anyList());
    }
    @Test void unresolvedSourceStopsEnrichmentEvenWhenProductAndKeywordWouldHideIt(){
        when(access.has("zsjos:sales-performance:detail")).thenReturn(true);
        when(access.historicalScope(any())).thenReturn(new PerformanceAccess.HistoricalScope(false,false,Set.of(12L)));
        var receipt=new PerformanceFact();receipt.setLeadId(5L);receipt.setReceivedAt(LocalDateTime.now(PerformancePeriods.ZONE).minusMinutes(1));
        receipt.setStatus("valid");receipt.setSourceType("education_self_sourced");receipt.setSourceProviderRecorded(false);
        when(details.conversionOrders(any())).thenReturn(List.of());when(details.conversionReceipts(any())).thenReturn(List.of(receipt));
        var q=new Query();q.setScopeType("DEPT");q.setScopeId(12L);q.setPeriodKey("last7");q.setKeyword("unmatched");q.setProductKey("unmatched");
        var response=service.conversionDetails(q);
        assertEquals("review_required",response.summary().sourceStatus());assertEquals(1,response.summary().sourceReviewCount());
        assertNull(response.summary().converted());assertNull(response.summary().denominator());assertNull(response.summary().rate());
        assertTrue(response.list().isEmpty());verifyNoInteractions(snapshots);
        var json=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(response.summary());
        assertTrue(json.contains("\"converted\":null"));assertTrue(json.contains("\"denominator\":null"));assertTrue(json.contains("\"rate\":null"));
    }
}
