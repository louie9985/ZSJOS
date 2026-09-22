package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class LeadOrderNotificationTargetServiceTest {
    @InjectMocks private LeadOrderNotificationTargetService service;
    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private LeadManagementService leadManagementService;

    @Test
    void resolvesOrderToLeadThroughAuthorizedService() {
        SalesOrderDO order = new SalesOrderDO();
        order.setLeadId(73L);
        when(salesOrderMapper.selectById(29L)).thenReturn(order);
        LeadManagementRespVO lead = new LeadManagementRespVO();
        lead.setId(73L);
        when(leadManagementService.getLead(73L, 5L)).thenReturn(lead);
        assertSame(lead, service.getLead(29L, 5L));
        verify(leadManagementService).getLead(73L, 5L);
    }

    @Test
    void propagatesLeadPermissionDenial() {
        SalesOrderDO order = new SalesOrderDO();
        order.setLeadId(73L);
        when(salesOrderMapper.selectById(29L)).thenReturn(order);
        when(leadManagementService.getLead(73L, 5L)).thenThrow(exception(LEAD_PERMISSION_DENIED));
        assertEquals(LEAD_PERMISSION_DENIED.getCode(),
                assertThrows(ServiceException.class, () -> service.getLead(29L, 5L)).getCode());
    }

    @Test
    void missingOrUnlinkedOrderDoesNotExposeOrderData() {
        assertEquals(LEAD_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> service.getLead(29L, 5L)).getCode());
        when(salesOrderMapper.selectById(29L)).thenReturn(new SalesOrderDO());
        assertEquals(LEAD_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> service.getLead(29L, 5L)).getCode());
        verifyNoInteractions(leadManagementService);
    }
}
