package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCompatibilityRecordDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_COLLABORATION_POOL_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_ENTRY_REQUIRES_POOL_EXIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadCollaborationServiceTest {
    @InjectMocks private LeadCollaborationService service;
    @Mock private LeadPublicSeaPoolService publicSeaPoolService;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolCycleMapper cycleMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolCompatibilityRecordMapper publicSeaPoolCompatibilityRecordMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper leadMapper;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void collaboratorCannotEnterDealBeforeFormalTransfer() {
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanEnterDealForUpdate(lead(), 30L));
        assertEquals(SALES_ORDER_ENTRY_REQUIRES_POOL_EXIT.getCode(), error.getCode());
    }

    @Test
    void formalOwnerCanEnterDealAndLocksPoolRows() {
        service.requireCanEnterDealForUpdate(lead(), 20L);
        verify(publicSeaPoolService).getActiveCycle(1L);
        verify(publicSeaPoolCompatibilityRecordMapper).selectByLeadIdForUpdate(1L, 1L);
    }

    @Test
    void historicalPoolOverlapStillFailsClosedForCollaboration() {
        LeadDO lead = lead();
        LeadPublicSeaPoolCycleDO cycle = new LeadPublicSeaPoolCycleDO(); cycle.setLeadId(1L);
        LeadPublicSeaPoolCompatibilityRecordDO manual = new LeadPublicSeaPoolCompatibilityRecordDO(); manual.setLeadId(1L);
        when(publicSeaPoolService.getActiveCycle(1L)).thenReturn(cycle);
        when(publicSeaPoolCompatibilityRecordMapper.selectByLeadIdForUpdate(1L, 1L)).thenReturn(manual);
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanOperateForUpdate(lead, 30L));
        assertEquals(LEAD_COLLABORATION_POOL_CONFLICT.getCode(), error.getCode());
        verify(publicSeaPoolService, never()).requireCanOperateForUpdate(any(), any(), any());
    }

    @Test
    void lockedPoolAllowsBothWritersButRejectsUnrelatedUsers() {
        var cycle = new LeadPublicSeaPoolCycleDO();
        cycle.setOriginalOwnerUserId(20L); cycle.setCollaboratorUserId(30L);
        when(cycleMapper.selectActiveByLeadIdForUpdate(1L, 1L)).thenReturn(cycle);
        for (String status : java.util.List.of("waiting_assignment", "assigned", "deal_pending")) {
            cycle.setStatus(status);
            service.requireCanWriteForUpdate(lead(), 20L, true);
            service.requireCanWriteForUpdate(lead(), 30L, true);
            assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.requireCanWriteForUpdate(lead(), 40L, true));
            if (!"deal_pending".equals(status)) service.requireCanWriteForUpdate(lead(), 20L, false);
            else assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.requireCanWriteForUpdate(lead(), 20L, false));
        }
        cycle.setStatus("assigned");
        service.requireCanWriteForUpdate(lead(), 30L, false);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanWriteForUpdate(lead(), 40L, false));
    }

    private static LeadDO lead() {
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setOwnerUserId(20L); return lead;
    }
}
