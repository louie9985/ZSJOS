package cn.iocoder.yudao.module.zsjos.service.notification;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerNotificationTargetServiceTest {
    @InjectMocks private PartnerNotificationTargetService service;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private LeadMapper leadMapper;

    @Test void ownedOrderResolvesExactLeadInsteadOfOrderIdOrList() {
        when(orderMapper.selectById(1L)).thenReturn(new SalesOrderDO().setLeadId(30L));
        when(leadMapper.selectById(30L)).thenReturn(new LeadDO().setId(30L).setPartnerId(8L));
        assertEquals("/lead/30", service.orderLeadPath(1L, 8L));
    }
    @Test void otherPartnerCannotResolveLead() {
        when(orderMapper.selectById(1L)).thenReturn(new SalesOrderDO().setLeadId(30L));
        when(leadMapper.selectById(30L)).thenReturn(new LeadDO().setId(30L).setPartnerId(9L));
        assertNull(service.orderLeadPath(1L, 8L));
    }
    @Test void missingOrUnlinkedOrderDoesNotGuess() {
        assertNull(service.orderLeadPath(1L, 8L));
        when(orderMapper.selectById(1L)).thenReturn(new SalesOrderDO());
        assertNull(service.orderLeadPath(1L, 8L));
        verifyNoInteractions(leadMapper);
    }
    @Test void deletedLeadOrMissingSubjectHasNoTarget() {
        when(orderMapper.selectById(1L)).thenReturn(new SalesOrderDO().setLeadId(30L));
        assertNull(service.orderLeadPath(1L, 8L));
        assertNull(service.orderLeadPath(1L, null));
    }
}
