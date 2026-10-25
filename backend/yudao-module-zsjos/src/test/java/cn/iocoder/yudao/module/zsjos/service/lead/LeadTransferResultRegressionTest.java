package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadTransferRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeadTransferResultRegressionTest {
    @Test void rejectedOrCancelledRequestsNeverTransferAndDuplicateEventsDoNothing() {
        TenantContextHolder.setTenantId(1L);
        try {
            for (var result : new BpmProcessInstanceStatusEnum[] {
                    BpmProcessInstanceStatusEnum.REJECT, BpmProcessInstanceStatusEnum.CANCEL}) {
                var service = new LeadTransferRequestServiceImpl();
                var requests = mock(LeadTransferRequestMapper.class);
                var dispatch = mock(LeadDispatchService.class);
                var notify = mock(LeadNotifyEventPublisher.class);
                ReflectionTestUtils.setField(service, "requestMapper", requests);
                ReflectionTestUtils.setField(service, "dispatchService", dispatch);
                ReflectionTestUtils.setField(service, "leadMapper", mock(LeadMapper.class));
                ReflectionTestUtils.setField(service, "notifyEventPublisher", notify);
                var row = new LeadTransferRequestDO();
                row.setId(1L); row.setLeadId(2L); row.setStatus("pending");
                row.setRequestedOwnerUserId(20L); row.setFromOwnerUserId(10L); row.setReason("fixture");
                when(requests.selectByProcessInstanceIdForUpdate("fixture", 1L)).thenReturn(row);
                service.handleProcessResult("fixture", result.getStatus(), "fixture decision");
                assertEquals(result == BpmProcessInstanceStatusEnum.REJECT ? "rejected" : "cancelled", row.getStatus());
                assertNotNull(row.getResolvedAt());
                service.handleProcessResult("fixture", result.getStatus(), "duplicate");
                verify(requests, times(1)).updateById(row);
                verifyNoInteractions(dispatch);
            }
        } finally { TenantContextHolder.clear(); }
    }
}
