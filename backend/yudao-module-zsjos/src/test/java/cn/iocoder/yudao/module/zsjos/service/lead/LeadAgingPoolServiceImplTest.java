package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadAgingPoolServiceImplTest {

    @InjectMocks private LeadAgingPoolServiceImpl service;
    @Mock private LeadAgingPoolCycleMapper cycleMapper;
    @Mock private LeadAgingPoolEventMapper eventMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private OpportunityMapper opportunityMapper;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void effectiveSalesFollowsActiveCycleState() {
        LeadAgingPoolCycleDO cycle = cycle(AGING_POOL_WAITING_ASSIGNMENT, null);
        when(cycleMapper.selectActiveByLeadId(1L)).thenReturn(cycle);
        assertNull(service.resolveEffectiveSalesUserId(1L, 10L));

        cycle.setStatus(AGING_POOL_ASSIGNED);
        cycle.setCollaboratorUserId(20L);
        assertEquals(20L, service.resolveEffectiveSalesUserId(1L, 10L));

        cycle.setStatus(AGING_POOL_DEAL_PENDING);
        assertEquals(20L, service.resolveEffectiveSalesUserId(1L, 10L));
    }

    @Test
    void orderPendingRejectionAndConversionPreserveABoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 12, 0);
        LeadAgingPoolCycleDO cycle = cycle(AGING_POOL_ASSIGNED, 20L);
        when(cycleMapper.selectActiveByLeadIdForUpdate(1L, 1L)).thenReturn(cycle);
        when(cycleMapper.updateWithVersion(cycle, 0)).thenReturn(1);
        when(cycleMapper.updateWithVersion(cycle, 1)).thenReturn(1);
        when(cycleMapper.updateWithVersion(cycle, 2)).thenReturn(1);

        service.markDealPending(1L, 20L, now);
        assertEquals(AGING_POOL_DEAL_PENDING, cycle.getStatus());

        service.handleOrderRejected(1L, now.plusMinutes(1));
        assertEquals(AGING_POOL_ASSIGNED, cycle.getStatus());

        cycle.setStatus(AGING_POOL_DEAL_PENDING);
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setOwnerUserId(10L);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(2L); opportunity.setLeadId(1L); opportunity.setOwnerUserId(10L);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(opportunityMapper.selectByLeadIdForUpdate(1L, 1L)).thenReturn(opportunity);

        service.completeConversion(1L, 20L, now.plusMinutes(2));

        assertEquals(20L, lead.getOwnerUserId());
        assertEquals(20L, opportunity.getOwnerUserId());
        assertEquals(AGING_POOL_CONVERTED, cycle.getStatus());
        verify(leadMapper).updateById(lead);
        verify(opportunityMapper).updateById(opportunity);
        verify(opportunityMapper).selectByLeadIdForUpdate(1L, 1L);
    }

    private static LeadAgingPoolCycleDO cycle(String status, Long collaboratorUserId) {
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setId(100L); cycle.setLeadId(1L); cycle.setOriginalOwnerUserId(10L);
        cycle.setCollaboratorUserId(collaboratorUserId); cycle.setFrozenDeptId(30L);
        cycle.setStatus(status); cycle.setCycleNo(1); cycle.setVersion(0);
        return cycle;
    }
}
