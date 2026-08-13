package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadAgingPoolServiceImplTest {

    @InjectMocks private LeadAgingPoolServiceImpl service;
    @Mock private LeadAgingPoolCycleMapper cycleMapper;
    @Mock private LeadAgingPoolEventMapper eventMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private OpportunityFollowUpRecordMapper opportunityFollowUpRecordMapper;
    @Mock private LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Mock private LeadFollowUpRuleService ruleService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private LeadNotifyEventPublisher notifyPublisher;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private SecurityFrameworkService securityFrameworkService;
    @Mock private AdvancedFilterService advancedFilterService;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); org.mockito.Mockito.lenient().when(advancedFilterService.matchLeadIds(org.mockito.ArgumentMatchers.any())).thenReturn(null); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void ownerAndConfiguredCollaboratorCanBothOperate() {
        LeadAgingPoolCycleDO cycle = cycle(AGING_POOL_WAITING_ASSIGNMENT, null);
        when(cycleMapper.selectActiveByLeadId(1L)).thenReturn(cycle);
        assertTrue(service.canOperate(1L, 10L, 10L));
        assertFalse(service.canOperate(1L, 10L, 20L));

        cycle.setStatus(AGING_POOL_ASSIGNED);
        cycle.setCollaboratorUserId(20L);
        assertTrue(service.canOperate(1L, 10L, 10L));
        assertTrue(service.canOperate(1L, 10L, 20L));

        cycle.setStatus(AGING_POOL_DEAL_PENDING);
        assertTrue(service.canOperate(1L, 10L, 10L));
        assertTrue(service.canOperate(1L, 10L, 20L));
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
        service.completeConversion(1L, 20L, now.plusMinutes(2));

        assertEquals(10L, lead.getOwnerUserId());
        assertEquals(10L, opportunity.getOwnerUserId());
        assertEquals(AGING_POOL_CONVERTED, cycle.getStatus());
        verify(leadMapper, never()).updateById(lead);
        verify(opportunityMapper, never()).updateById(opportunity);
    }

    @Test
    void publicSeaVisibilityUsesFormalOwnersCurrentDepartment() {
        LeadAgingPoolCycleDO cycle = cycle(AGING_POOL_WAITING_ASSIGNMENT, null);
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(10L); owner.setDeptId(40L);
        AdminUserRespDTO currentTeamSales = new AdminUserRespDTO(); currentTeamSales.setId(20L); currentTeamSales.setDeptId(40L);
        AdminUserRespDTO formerTeamSales = new AdminUserRespDTO(); formerTeamSales.setId(30L); formerTeamSales.setDeptId(30L);
        when(cycleMapper.selectActiveByLeadId(1L)).thenReturn(cycle);
        when(adminUserApi.getUser(10L)).thenReturn(owner);
        when(adminUserApi.getUser(20L)).thenReturn(currentTeamSales);
        when(adminUserApi.getUser(30L)).thenReturn(formerTeamSales);
        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO current =
                new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO();
        current.setId(20L);
        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO former =
                new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO();
        former.setId(30L);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(java.util.List.of(current, former));

        assertTrue(service.canRead(1L, 20L));
        assertFalse(service.canRead(1L, 30L));
    }

    @Test
    void preQualificationNoProgressWarnsThenReleasesToClaimPool() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 12, 0);
        LeadFollowUpRuleDO rule = new LeadFollowUpRuleDO();
        rule.setNoProgressWarningDays(7); rule.setNoProgressGraceDays(2);
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus(STATUS_SUBMITTED); lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setOwnerUserId(10L); lead.setOwnershipStartedAt(now.minusDays(10));
        when(ruleService.requireEnabledRule()).thenReturn(rule);
        when(leadMapper.selectPreQualificationNoProgressCandidates(now.minusDays(7))).thenReturn(java.util.List.of(lead));
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);

        assertEquals(1, service.processPreQualificationNoProgress(now));
        assertEquals(now, lead.getNoProgressWarnedAt());

        lead.setNoProgressWarnedAt(now.minusDays(3));
        assertEquals(1, service.processPreQualificationNoProgress(now));
        assertEquals(ASSIGNMENT_PUBLIC_POOL, lead.getAssignmentStatus());
        assertNull(lead.getOwnerUserId());
        verify(assignmentHistoryMapper).insert(org.mockito.ArgumentMatchers.any(LeadAssignmentHistoryDO.class));
    }

    private static LeadAgingPoolCycleDO cycle(String status, Long collaboratorUserId) {
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setId(100L); cycle.setLeadId(1L); cycle.setOriginalOwnerUserId(10L);
        cycle.setCollaboratorUserId(collaboratorUserId); cycle.setFrozenDeptId(30L);
        cycle.setStatus(status); cycle.setCycleNo(1); cycle.setVersion(0);
        return cycle;
    }
}
