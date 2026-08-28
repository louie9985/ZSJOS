package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaRecordDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_COLLABORATION_POOL_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_ENTRY_REQUIRES_TRANSFER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadCollaborationServiceTest {
    @InjectMocks private LeadCollaborationService service;
    @Mock private LeadAgingPoolService agingPoolService;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper leadMapper;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void collaboratorCannotEnterDealBeforeFormalTransfer() {
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanEnterDealForUpdate(lead(), 30L));
        assertEquals(SALES_ORDER_ENTRY_REQUIRES_TRANSFER.getCode(), error.getCode());
    }

    @Test
    void formalOwnerCanEnterDealAndLocksPoolRows() {
        service.requireCanEnterDealForUpdate(lead(), 20L);
        verify(agingPoolService).getActiveCycle(1L);
        verify(publicSeaRecordMapper).selectByLeadIdForUpdate(1L, 1L);
    }

    @Test
    void historicalPoolOverlapStillFailsClosedForCollaboration() {
        LeadDO lead = lead();
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO(); cycle.setLeadId(1L);
        LeadPublicSeaRecordDO manual = new LeadPublicSeaRecordDO(); manual.setLeadId(1L);
        when(agingPoolService.getActiveCycle(1L)).thenReturn(cycle);
        when(publicSeaRecordMapper.selectByLeadIdForUpdate(1L, 1L)).thenReturn(manual);
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanOperateForUpdate(lead, 30L));
        assertEquals(LEAD_COLLABORATION_POOL_CONFLICT.getCode(), error.getCode());
        verify(agingPoolService, never()).requireCanOperateForUpdate(any(), any(), any());
    }

    private static LeadDO lead() {
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setOwnerUserId(20L); return lead;
    }
}
