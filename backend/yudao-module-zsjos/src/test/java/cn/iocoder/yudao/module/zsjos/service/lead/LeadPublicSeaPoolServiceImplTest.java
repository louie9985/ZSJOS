package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaPoolCompatibilityRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class LeadPublicSeaPoolServiceImplTest {

    @InjectMocks private LeadPublicSeaPoolServiceImpl service;
    @Mock private cn.iocoder.yudao.module.zsjos.service.lead.flow.LeadFlowRecorder flowRecorder;
    @Mock private LeadPublicSeaPoolCycleMapper cycleMapper;
    @Mock private LeadPublicSeaPoolEventMapper eventMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private OpportunityFollowUpRecordMapper opportunityFollowUpRecordMapper;
    @Mock private LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Mock private LeadFollowUpRuleService ruleService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private LeadPublicSeaTaskCleanupService taskCleanupService;
    @Mock private LeadNotifyEventPublisher notifyPublisher;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    @Mock private SecurityFrameworkService securityFrameworkService;
    @Mock private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    @Mock private LeadInboxFilterConfigService inboxFilterConfigService;
    @Mock private AdvancedFilterService advancedFilterService;
    @Mock private LeadPublicSeaPoolCompatibilityRecordMapper publicSeaPoolCompatibilityRecordMapper;
    @Mock private SalesOrderMapper orderMapper;

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(1L); org.mockito.Mockito.lenient().when(advancedFilterService.matchLeadIds(org.mockito.ArgumentMatchers.any())).thenReturn(null); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test void administratorCountsIgnoreDepartmentWithoutGrantingSharedCommandEligibility() {
        when(permissionApi.hasTenantReadAllAccess(42L)).thenReturn(true);
        assertEquals(0L, service.getCounts(42L).get("all"));
        verify(cycleMapper).selectCountByStatus(null, null, PUBLIC_SEA_POOL_WAITING_ASSIGNMENT);
        verify(cycleMapper).selectCountByStatus(null, null, PUBLIC_SEA_POOL_ASSIGNED);
        assertFalse(service.canOperate(1L, 10L, 42L));
    }

    @Test
    void personalQueryUsesCallerIdentityForManageAllAndPreservesAdvancedStatusIntersection() {
        var req = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.publicseapool.LeadPublicSeaPoolPageReqVO();
        req.setRelationScope(PUBLIC_SEA_POOL_RELATION_OWNED);
        req.setInboxGroup("all");
        req.setInboxStage("assigned");
        when(securityFrameworkService.hasPermission(PERMISSION_PUBLIC_SEA_POOL_MANAGE_ALL)).thenReturn(true);
        when(inboxFilterConfigService.resolveQuery(any(), eq("all"), eq("assigned")))
                .thenReturn(new LeadInboxFilterQuery(java.util.Set.of(), java.util.Set.of(), false,
                        java.util.Map.of(INBOX_FILTER_FIELD_POOL_STATUS, java.util.Set.of(PUBLIC_SEA_POOL_ASSIGNED))));
        when(advancedFilterService.matchLeadIds(any())).thenReturn(java.util.List.of(99L));
        when(cycleMapper.selectPage(eq(req), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                eq(java.util.List.of(PUBLIC_SEA_POOL_ASSIGNED)), eq(false), eq(java.util.List.of(99L)), eq(42L)))
                .thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(java.util.List.of(), 0L));
        assertEquals(0L, service.getPage(req, 42L).getTotal());
        verify(cycleMapper).selectPage(req, null, null, java.util.List.of(PUBLIC_SEA_POOL_ASSIGNED), false, java.util.List.of(99L), 42L);
    }

    @Test
    void onlyConfiguredCollaboratorCanOperateDuringPublicSea() {
        LeadPublicSeaPoolCycleDO cycle = cycle(PUBLIC_SEA_POOL_WAITING_ASSIGNMENT, null);
        when(cycleMapper.selectActiveByLeadId(1L)).thenReturn(cycle);
        assertFalse(service.canOperate(1L, 10L, 10L));
        assertFalse(service.canOperate(1L, 10L, 20L));

        cycle.setStatus(PUBLIC_SEA_POOL_ASSIGNED);
        cycle.setCollaboratorUserId(20L);
        assertFalse(service.canOperate(1L, 10L, 10L));
        assertTrue(service.canOperate(1L, 10L, 20L));

        cycle.setStatus(PUBLIC_SEA_POOL_DEAL_PENDING);
        assertFalse(service.canOperate(1L, 10L, 10L));
        assertTrue(service.canOperate(1L, 10L, 20L));
    }

    @Test
    void manualPublicSeaAllowsOnlyConfiguredCollaboratorToFollowUp() {
        var manual = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCompatibilityRecordDO();
        manual.setOwnerUserId(10L); manual.setCollaboratorUserId(20L);
        when(cycleMapper.selectActiveByLeadId(1L)).thenReturn(null);
        when(publicSeaPoolCompatibilityRecordMapper.selectByLeadId(1L)).thenReturn(manual);

        assertFalse(service.canOperate(1L, 10L, 10L));
        assertTrue(service.canOperate(1L, 10L, 20L));
        assertFalse(service.canOperate(1L, 10L, 30L));
    }

    @Test
    void collaboratorRetainsFollowUpButNotDealActions() {
        LeadPublicSeaPoolCycleDO cycle = cycle(PUBLIC_SEA_POOL_ASSIGNED, 20L);
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setOwnerUserId(10L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(cycleMapper.selectById(100L)).thenReturn(cycle);
        when(cycleMapper.selectActiveByLeadId(1L)).thenReturn(cycle);
        when(orderMapper.selectActiveByLeadId(1L, cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ACTIVE_ORDER_STATUSES)).thenReturn(null);
        when(securityFrameworkService.hasPermission(anyString())).thenReturn(false);
        when(securityFrameworkService.hasPermission("zsjos:lead-follow-up:create")).thenReturn(true);
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(10L); owner.setDeptId(30L);
        AdminUserRespDTO collaboratorUser = new AdminUserRespDTO(); collaboratorUser.setId(20L); collaboratorUser.setDeptId(30L);
        when(adminUserApi.getUser(10L)).thenReturn(owner);
        when(adminUserApi.getUser(20L)).thenReturn(collaboratorUser);

        var collaborator = service.get(100L, 20L);
        assertTrue(collaborator.getAvailableActions().contains(ACTION_ADD_FOLLOW_UP));
        assertFalse(collaborator.getAvailableActions().contains(ACTION_ENTER_DEAL));
        assertFalse(collaborator.getAvailableActions().contains(ACTION_REVISE_DEAL));
    }

    @Test
    void orderPendingRejectionAndConversionPreserveABoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 12, 0);
        LeadPublicSeaPoolCycleDO cycle = cycle(PUBLIC_SEA_POOL_ASSIGNED, 20L);
        when(cycleMapper.selectActiveByLeadIdForUpdate(1L, 1L)).thenReturn(cycle);
        when(cycleMapper.updateWithVersion(cycle, 0)).thenReturn(1);
        when(cycleMapper.updateWithVersion(cycle, 1)).thenReturn(1);
        when(cycleMapper.updateWithVersion(cycle, 2)).thenReturn(1);

        service.markDealPending(1L, 20L, now);
        assertEquals(PUBLIC_SEA_POOL_DEAL_PENDING, cycle.getStatus());

        service.handleOrderRejected(1L, now.plusMinutes(1));
        assertEquals(PUBLIC_SEA_POOL_ASSIGNED, cycle.getStatus());

        cycle.setStatus(PUBLIC_SEA_POOL_DEAL_PENDING);
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setOwnerUserId(10L);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(2L); opportunity.setLeadId(1L); opportunity.setOwnerUserId(10L);
        service.completeConversion(1L, 20L, now.plusMinutes(2));

        assertEquals(10L, lead.getOwnerUserId());
        assertEquals(10L, opportunity.getOwnerUserId());
        assertEquals(PUBLIC_SEA_POOL_CONVERTED, cycle.getStatus());
        verify(leadMapper, never()).updateById(lead);
        verify(opportunityMapper, never()).updateById(opportunity);
        verify(leadMapper).touchActivity(1L, now);
        verify(leadMapper).touchActivity(1L, now.plusMinutes(1));
        verify(leadMapper).touchActivity(1L, now.plusMinutes(2));
    }

    @Test
    void publicSeaVisibilityUsesFormalOwnersCurrentDepartment() {
        LeadPublicSeaPoolCycleDO cycle = cycle(PUBLIC_SEA_POOL_WAITING_ASSIGNMENT, null);
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
    void dueLeadDoesNotEnterPublicSeaPoolWhileManualPublicSeaIsActive() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 9, 0);
        LeadFollowUpRuleDO rule = new LeadFollowUpRuleDO(); rule.setPublicSeaPoolTimeoutDays(7);
        LeadDO lead = new LeadDO(); lead.setId(1L);
        when(ruleService.requireEnabledRule()).thenReturn(rule);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(publicSeaPoolCompatibilityRecordMapper.selectByLeadIdForUpdate(1L, 1L))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCompatibilityRecordDO());

        assertFalse(service.tryEnterDueLead(1L, now));

        verify(cycleMapper, never()).insert(org.mockito.ArgumentMatchers.any(LeadPublicSeaPoolCycleDO.class));
        verify(leadMapper, never()).touchActivity(anyLong(), any());
    }

    @Test
    void manualEntryPreservesOwnerAndStoresConfiguredCollaborator() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus(STATUS_VALID); lead.setAssignmentStatus(ASSIGNMENT_OWNED);
        lead.setOwnerUserId(10L);
        OpportunityDO opportunity = new OpportunityDO(); opportunity.setStatus(OPPORTUNITY_STATUS_FOLLOWING);
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(10L); owner.setDeptId(30L);
        AdminUserRespDTO collaborator = new AdminUserRespDTO(); collaborator.setId(20L); collaborator.setDeptId(30L);
        collaborator.setStatus(0);
        var candidate = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO();
        candidate.setId(20L);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        when(adminUserApi.getUser(10L)).thenReturn(owner);
        when(adminUserApi.getUserListByDeptIds(java.util.List.of(30L))).thenReturn(java.util.List.of(collaborator));
        when(assignmentService.getEligibleSalesUsers()).thenReturn(java.util.List.of(candidate));
        when(cycleMapper.selectNextCycleNo(1L)).thenReturn(2);

        service.enterManually(1L, 20L, 99L, "主管释放", "manual-1");

        ArgumentCaptor<LeadPublicSeaPoolCycleDO> captor = ArgumentCaptor.forClass(LeadPublicSeaPoolCycleDO.class);
        verify(cycleMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getOriginalOwnerUserId());
        assertEquals(20L, captor.getValue().getCollaboratorUserId());
        assertEquals(PUBLIC_SEA_POOL_ASSIGNED, captor.getValue().getStatus());
        assertEquals(10L, lead.getOwnerUserId());
        verify(taskCleanupService).cancelObsolete(eq(1L), any(LocalDateTime.class));
        verify(leadMapper, never()).updateById(any(LeadDO.class));
        verify(leadMapper).touchActivity(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void writeBoundaryRejectsAAndUnassignedButAcceptsCurrentB() {
        LeadPublicSeaPoolCycleDO cycle = cycle(PUBLIC_SEA_POOL_WAITING_ASSIGNMENT, null);
        when(cycleMapper.selectActiveByLeadIdForUpdate(1L, 1L)).thenReturn(cycle);
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanOperateForUpdate(1L, 10L, 10L));
        cycle.setStatus(PUBLIC_SEA_POOL_ASSIGNED); cycle.setCollaboratorUserId(20L);
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanOperateForUpdate(1L, 10L, 10L));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.requireCanOperateForUpdate(1L, 10L, 20L));
        cycle.setCollaboratorUserId(30L);
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.requireCanOperateForUpdate(1L, 10L, 20L));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.requireCanOperateForUpdate(1L, 10L, 30L));
    }

    @Test
    void managerExitRestoresPendingTasksToFormalOwner() {
        LeadPublicSeaPoolCycleDO cycle = cycle(PUBLIC_SEA_POOL_ASSIGNED, 20L);
        LeadDO lead = new LeadDO(); lead.setId(1L); lead.setOwnerUserId(10L);
        AdminUserRespDTO owner = new AdminUserRespDTO(); owner.setId(10L); owner.setStatus(0);
        when(cycleMapper.selectByIdForUpdate(100L, 1L)).thenReturn(cycle);
        when(cycleMapper.updateWithVersion(cycle, 0)).thenReturn(1);
        when(securityFrameworkService.hasPermission(PERMISSION_PUBLIC_SEA_POOL_MANAGE_ALL)).thenReturn(true);
        when(adminUserApi.getUser(10L)).thenReturn(owner);
        when(leadMapper.selectByIdForUpdate(1L, 1L)).thenReturn(lead);
        var req = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.publicseapool.LeadPublicSeaPoolExitReqVO();
        req.setReason("恢复原负责人跟进"); req.setIdempotencyKey("exit-follow-up");
        service.exit(100L, 99L, req);
        var cleanupOrder = org.mockito.Mockito.inOrder(taskCleanupService, lifecycleTaskService);
        cleanupOrder.verify(taskCleanupService).cancelObsolete(eq(1L), any(LocalDateTime.class));
        cleanupOrder.verify(lifecycleTaskService).reassignPendingSalesTasks(1L, 10L);
        assertEquals(10L, lead.getOwnerUserId()); assertEquals(PUBLIC_SEA_POOL_EXITED, cycle.getStatus());
    }

    private static LeadPublicSeaPoolCycleDO cycle(String status, Long collaboratorUserId) {
        LeadPublicSeaPoolCycleDO cycle = new LeadPublicSeaPoolCycleDO();
        cycle.setId(100L); cycle.setLeadId(1L); cycle.setOriginalOwnerUserId(10L);
        cycle.setCollaboratorUserId(collaboratorUserId); cycle.setFrozenDeptId(30L);
        cycle.setStatus(status); cycle.setCycleNo(1); cycle.setVersion(0);
        return cycle;
    }
}
