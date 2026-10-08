package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.service.order.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderSortServiceTest {
    @Test void sortedRequestsPreserveScopeFilterAndRequestWithoutSortingCurrentPageOnly() {
        var service=new SalesOrderServiceImpl();var mapper=mock(SalesOrderMapper.class);
        var access=mock(SalesOrderObjectPermissionService.class);var filters=mock(AdvancedFilterService.class);
        ReflectionTestUtils.setField(service,"orderMapper",mapper);ReflectionTestUtils.setField(service,"permissionService",access);
        ReflectionTestUtils.setField(service,"advancedFilterService",filters);
        ReflectionTestUtils.setField(service,"roundMapper",mock(SalesOrderApprovalRoundMapper.class));
        var items=mock(SalesOrderItemMapper.class);ReflectionTestUtils.setField(service,"itemMapper",items);
        var scope=new SalesOrderManagementScope(false,true,Set.of(),Set.of(7L));
        when(access.resolveManagementScope(7L)).thenReturn(scope);when(filters.matchOrderIds(any())).thenReturn(List.of(1L,2L));
        var request=new SalesOrderMyPageReqVO();request.setSortField("studentName");request.setSortOrder("ascend");request.setPageSize(1);request.setPageNo(2);request.setStatus("effective");request.setKeyword("筛选");
        when(mapper.selectManagementPage(eq(scope),any(),eq(List.of(1L,2L)))).thenAnswer(call->{
            SalesOrderMyPageReqVO batch=call.getArgument(1);assertNull(batch.getSortField());assertEquals("effective",batch.getStatus());assertEquals("筛选",batch.getKeyword());assertEquals(200,batch.getPageSize());
            return new PageResult<>(List.of(new SalesOrderDO().setId(1L).setStudentName("张").setStatus("effective"),new SalesOrderDO().setId(2L).setStudentName("陈").setStatus("effective")),2L);
        });
        when(items.selectListByOrderIds(any())).thenReturn(List.of());
        var result=service.getManagementPage(request,7L);
        assertEquals(2L,result.getTotal());assertEquals(1L,result.getList().getFirst().getId());
        assertEquals(2,request.getPageNo());assertEquals("studentName",request.getSortField());
    }
}
