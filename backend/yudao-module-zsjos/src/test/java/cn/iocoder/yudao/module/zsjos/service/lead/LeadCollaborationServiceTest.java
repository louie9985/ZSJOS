package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ACTION_TRANSFER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadCollaborationServiceTest {

    @InjectMocks private LeadCollaborationService service;
    @Mock private LeadAgingPoolService agingPoolService;
    @Mock private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void manualPublicSeaCollaboratorBecomesFormalOwnerAndRebindsTasks() {
        LeadDO lead = lead();
        OpportunityDO opportunity = opportunity();
        LeadPublicSeaRecordDO record = new LeadPublicSeaRecordDO();
        record.setLeadId(1L);
        record.setOwnerUserId(20L);
        record.setCollaboratorUserId(30L);
        when(publicSeaRecordMapper.selectByLeadIdForUpdate(1L, 1L)).thenReturn(record);
        doAnswer(invocation -> {
            invocation.<LeadAssignmentHistoryDO>getArgument(0).setId(50L);
            return 1;
        }).when(assignmentHistoryMapper).insert(any(LeadAssignmentHistoryDO.class));

        LeadCollaborationService.OperationContext context = service.requireCanOperateForUpdate(lead, 30L);
        LocalDateTime transferredAt = LocalDateTime.of(2026, 8, 18, 17, 0);
        service.transferOnOrderSubmission(lead, opportunity, context, 30L, transferredAt);

        assertTrue(context.collaborator());
        assertEquals("manual_public_sea", context.poolType());
        assertEquals(30L, lead.getOwnerUserId());
        assertEquals(30L, opportunity.getOwnerUserId());
        assertEquals(50L, lead.getCurrentAssignmentHistoryId());
        assertEquals(transferredAt, lead.getOwnershipStartedAt());
        verify(publicSeaRecordMapper).deleteByLeadId(1L);
        verify(leadMapper).updateById(lead);
        verify(opportunityMapper).updateById(opportunity);
        verify(lifecycleTaskService).reassignPendingSalesTasks(1L, 30L);

        ArgumentCaptor<LeadAssignmentHistoryDO> historyCaptor =
                ArgumentCaptor.forClass(LeadAssignmentHistoryDO.class);
        verify(assignmentHistoryMapper).insert(historyCaptor.capture());
        LeadAssignmentHistoryDO history = historyCaptor.getValue();
        assertEquals(ACTION_TRANSFER, history.getActionType());
        assertEquals(20L, history.getFromOwnerUserId());
        assertEquals(30L, history.getToOwnerUserId());
        assertEquals(30L, history.getOperatorUserId());
    }

    @Test
    void agingPoolCollaboratorTerminatesCycleBeforeOwnershipTransfer() {
        LeadDO lead = lead();
        OpportunityDO opportunity = opportunity();
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setLeadId(1L);
        cycle.setOriginalOwnerUserId(20L);
        cycle.setCollaboratorUserId(30L);
        when(agingPoolService.getActiveCycle(1L)).thenReturn(cycle);
        doAnswer(invocation -> {
            invocation.<LeadAssignmentHistoryDO>getArgument(0).setId(51L);
            return 1;
        }).when(assignmentHistoryMapper).insert(any(LeadAssignmentHistoryDO.class));

        LeadCollaborationService.OperationContext context = service.requireCanOperateForUpdate(lead, 30L);
        LocalDateTime transferredAt = LocalDateTime.of(2026, 8, 18, 17, 30);
        service.transferOnOrderSubmission(lead, opportunity, context, 30L, transferredAt);

        assertTrue(context.collaborator());
        assertEquals("aging_pool", context.poolType());
        verify(agingPoolService).requireCanOperateForUpdate(1L, 20L, 30L);
        verify(agingPoolService).terminateForOwnerTransfer(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(transferredAt),
                contains("正式归属转移"));
        verify(publicSeaRecordMapper, never()).deleteByLeadId(1L);
        assertEquals(30L, lead.getOwnerUserId());
        assertEquals(30L, opportunity.getOwnerUserId());
    }

    @Test
    void formalOwnerSubmissionDoesNotCreateAnotherTransfer() {
        LeadDO lead = lead();
        OpportunityDO opportunity = opportunity();
        when(agingPoolService.getActiveCycle(1L)).thenReturn(null);

        LeadCollaborationService.OperationContext context = service.requireCanOperateForUpdate(lead, 20L);
        service.transferOnOrderSubmission(lead, opportunity, context, 20L, LocalDateTime.now());

        assertFalse(context.collaborator());
        verify(publicSeaRecordMapper).selectByLeadIdForUpdate(1L, 1L);
        verifyNoInteractions(assignmentHistoryMapper, leadMapper, opportunityMapper, lifecycleTaskService);
    }

    @Test
    void historicalPoolOverlapFailsClosed() {
        LeadDO lead = lead();
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setLeadId(1L);
        LeadPublicSeaRecordDO manual = new LeadPublicSeaRecordDO();
        manual.setLeadId(1L); manual.setOwnerUserId(20L); manual.setCollaboratorUserId(30L);
        when(agingPoolService.getActiveCycle(1L)).thenReturn(cycle);
        when(publicSeaRecordMapper.selectByLeadIdForUpdate(1L, 1L)).thenReturn(manual);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanOperateForUpdate(lead, 30L));

        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_COLLABORATION_POOL_CONFLICT.getCode(),
                error.getCode());
        verify(agingPoolService, never()).requireCanOperateForUpdate(any(), any(), any());
    }

    private static LeadDO lead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setOwnerUserId(20L);
        return lead;
    }

    private static OpportunityDO opportunity() {
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(2L);
        opportunity.setLeadId(1L);
        opportunity.setOwnerUserId(20L);
        return opportunity;
    }
}
