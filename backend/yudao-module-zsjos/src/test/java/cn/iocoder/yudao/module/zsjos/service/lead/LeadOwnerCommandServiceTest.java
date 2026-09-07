package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ACTIVE_ORDER_STATUSES;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_PENDING_APPROVAL;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_REVISION_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_OWNER_TRANSFER_DEAL_ACTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadOwnerCommandServiceTest {

    @InjectMocks private LeadOwnerCommandService service;
    @Mock private LeadMapper leadMapper;
    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private LeadDispatchService dispatchService;
    @Mock private LeadAgingPoolService agingPoolService;
    @Mock private LeadAssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void transferRejectsPendingApprovalOrder() {
        assertActiveOrderRejected(STATUS_PENDING_APPROVAL);
    }

    @Test
    void transferRejectsRevisionRequiredOrder() {
        assertActiveOrderRejected(STATUS_REVISION_REQUIRED);
    }

    @Test
    void transferAllowsLeadWithoutActiveOrder() {
        LeadDO lead = ownedLead();
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(salesOrderMapper.selectActiveByLeadId(1L, ACTIVE_ORDER_STATUSES)).thenReturn(null);

        service.transfer(1L, 30L, 20L, "工作调整", "key-1");

        verify(dispatchService).transferOwned(1L, 20L, 30L, 20L, "工作调整", "key-1");
    }

    @Test
    void transferAllowsLeadAfterOrderTermination() {
        LeadDO lead = ownedLead();
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(salesOrderMapper.selectActiveByLeadId(1L, ACTIVE_ORDER_STATUSES)).thenReturn(null);

        service.transfer(1L, 30L, 20L, "订单已终止", "key-2");

        verify(dispatchService).transferOwned(1L, 20L, 30L, 20L, "订单已终止", "key-2");
    }

    @Test
    void transferRejectsWonLeadAfterApprovalCompletes() {
        LeadDO lead = ownedLead();
        lead.setStatus("won");
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.transfer(1L, 30L, 20L, "工作调整", "key-1"));

        assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        verify(salesOrderMapper, never()).selectActiveByLeadId(1L, ACTIVE_ORDER_STATUSES);
        verify(dispatchService, never()).transferOwned(1L, 20L, 30L, 20L, "工作调整", "key-1");
    }

    private void assertActiveOrderRejected(String status) {
        LeadDO lead = ownedLead();
        SalesOrderDO order = new SalesOrderDO();
        order.setId(40L);
        order.setStatus(status);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(salesOrderMapper.selectActiveByLeadId(1L, ACTIVE_ORDER_STATUSES)).thenReturn(order);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.transfer(1L, 30L, 20L, "工作调整", "key-1"));

        assertEquals(LEAD_OWNER_TRANSFER_DEAL_ACTIVE.getCode(), error.getCode());
        verify(dispatchService, never()).transferOwned(1L, 20L, 30L, 20L, "工作调整", "key-1");
    }

    private LeadDO ownedLead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setOwnerUserId(20L);
        return lead;
    }
}
