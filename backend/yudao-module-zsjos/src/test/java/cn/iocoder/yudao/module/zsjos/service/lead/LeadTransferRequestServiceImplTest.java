package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadTransferRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadTransferRequestServiceImplTest {
    @InjectMocks private LeadTransferRequestServiceImpl service;
    @Mock private LeadTransferRequestMapper requestMapper;
    @Mock private LeadDispatchService dispatchService;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void approvedBpmResultExecutesFormalTransfer() {
        LeadTransferRequestDO request = new LeadTransferRequestDO();
        request.setId(1L); request.setLeadId(2L); request.setFromOwnerUserId(10L);
        request.setRequestedOwnerUserId(20L);
        request.setReason("本人持续跟进"); request.setStatus("pending");
        when(requestMapper.selectByProcessInstanceIdForUpdate("p1", 1L)).thenReturn(request);
        when(dispatchService.tryAdminTransfer(2L, 10L, 20L, 20L,
                "同团队销售转派申请审批通过：本人持续跟进"))
                .thenReturn(LeadDispatchService.TransferAttemptResult.success());

        service.handleProcessResult("p1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "通过");

        verify(dispatchService).tryAdminTransfer(2L, 10L, 20L, 20L,
                "同团队销售转派申请审批通过：本人持续跟进");
        assertEquals("approved", request.getStatus());
        verify(requestMapper).updateById(request);
    }
}
