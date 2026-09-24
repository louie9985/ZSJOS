package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.WithdrawalMapper;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackServiceImpl;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancedFilterFinanceQueryTest {
    @Test void cashbackMatchesAreIntersectedWithPersonalScopeBeforePaging() {
        var service = new CashbackServiceImpl();
        var mapper = mock(CashbackMapper.class);
        var filters = mock(AdvancedFilterService.class);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "advancedFilterService", filters);
        var request = new CashbackPageReqVO(); request.setAdvancedFilter(new AdvancedFilterGroupReqVO());
        when(filters.matchFinanceIds("cashback", request.getAdvancedFilter())).thenReturn(List.of(2L));
        when(mapper.selectCashbackPage(request, 7L, List.of(2L))).thenReturn(new PageResult<>(List.of(), 0L));
        assertEquals(0L, service.getPage(request, 7L).getTotal());
        verify(mapper).selectCashbackPage(request, 7L, List.of(2L));
        verify(mapper, never()).selectCashbackPage(request, 7L);
    }
    @Test void withdrawalManagementPersonalAndExportAllConsumeTheSameFilters() {
        var service = new WithdrawalServiceImpl();
        var mapper = mock(WithdrawalMapper.class);
        var filters = mock(AdvancedFilterService.class);
        ReflectionTestUtils.setField(service, "withdrawalMapper", mapper);
        ReflectionTestUtils.setField(service, "advancedFilterService", filters);
        var request = new WithdrawalPageReqVO(); request.setAdvancedFilter(new AdvancedFilterGroupReqVO());
        when(filters.matchFinanceIds("withdrawal", request.getAdvancedFilter())).thenReturn(List.of());
        when(mapper.selectPageByApplicant(request, 7L, List.of())).thenReturn(new PageResult<>(List.of(), 0L));
        when(mapper.selectPageByApplicant(request, null, List.of())).thenReturn(new PageResult<>(List.of(), 0L));
        assertEquals(0L, service.getPage(request, 7L).getTotal());
        assertEquals(0L, service.getManagementPage(request).getTotal());
        assertEquals(0L, service.getPage(request, null).getTotal());
        verify(mapper).selectPageByApplicant(request, 7L, List.of());
        verify(mapper, times(2)).selectPageByApplicant(request, null, List.of());
    }
    @Test void emptyMatchesNeverFallBackToAnUnfilteredPage() {
        var cashback = mock(CashbackMapper.class, CALLS_REAL_METHODS);
        var withdrawal = mock(WithdrawalMapper.class, CALLS_REAL_METHODS);
        assertEquals(0L, cashback.selectCashbackPage(new CashbackPageReqVO(), 7L, List.of()).getTotal());
        assertEquals(0L, withdrawal.selectPageByApplicant(new WithdrawalPageReqVO(), 7L, List.of()).getTotal());
    }
}
