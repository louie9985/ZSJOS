package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadPublicSeaRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class LeadObjectPermissionServiceTest {

    @InjectMocks
    private LeadObjectPermissionService service;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private SecurityFrameworkService securityFrameworkService;
    @Mock private DeptApi deptApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private LeadAgingPoolCycleMapper agingPoolCycleMapper;
    @Mock private ServiceRelationMapper serviceRelationMapper;
    @Mock private LeadPublicSeaRecordMapper publicSeaRecordMapper;
    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private SalesOrderObjectPermissionService salesOrderObjectPermissionService;
    @Mock private LeadAgingPoolService leadAgingPoolService;
    @Mock private MediaAccountMapper mediaAccountMapper;

    @BeforeEach
    void setUpPermissionDefaults() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        org.mockito.Mockito.lenient().when(securityFrameworkService.hasPermission(
                org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    }

    @AfterEach
    void clearTenant() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
    }

    @Test
    void readAllowsOriginalSubmitter() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));

        assertReadAllowed(10L);
    }

    @Test
    void readAllowsCurrentOwner() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));

        assertReadAllowed(20L);
    }

    @Test
    void ownerReadAllowsCurrentOwner() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));

        assertActionAllowed(20L, "owner-read");
    }

    @Test
    void ownerReadRejectsOriginalSubmitter() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);

            ServiceException error = assertThrows(ServiceException.class, () -> service.check(1L, "owner-read"));

            assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        }
    }

    @Test
    void ownerOrManagerReadAllowsCurrentOwnerAndRejectsSubmitter() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));

        assertActionAllowed(20L, "owner-or-manager-read");
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(10L);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.check(1L, "owner-or-manager-read"));
            assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        }
    }

    @Test
    void ownerOrManagerReadAllowsDirectAndParentDepartmentLeaders() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 102L));
        when(deptApi.getDeptListByLeaderUserId(30L)).thenReturn(List.of(dept(102L)));
        assertActionAllowed(30L, "owner-or-manager-read");

        when(deptApi.getDeptListByLeaderUserId(40L)).thenReturn(List.of(dept(100L)));
        when(deptApi.getChildDeptList(100L)).thenReturn(List.of(dept(101L), dept(102L)));
        assertActionAllowed(40L, "owner-or-manager-read");
    }

    @Test
    void ownerOrManagerReadAllowsQueryAllAndRejectsParallelLeader() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(true);
        assertActionAllowed(50L, "owner-or-manager-read");

        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(false);
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 102L));
        when(deptApi.getDeptListByLeaderUserId(60L)).thenReturn(List.of(dept(101L)));
        when(deptApi.getChildDeptList(101L)).thenReturn(List.of());
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(60L);
            assertThrows(ServiceException.class, () -> service.check(1L, "owner-or-manager-read"));
        }
    }

    @Test
    void readAllowsQueryAllPermission() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(true);

        assertReadAllowed(30L);
    }

    @Test
    void readRejectsUnrelatedUserWithoutQueryAll() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(false);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(30L);

            ServiceException error = assertThrows(ServiceException.class, () -> service.check(1L, "read"));

            assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        }
    }

    @Test
    void readRejectsMissingLead() {
        when(leadMapper.selectById(1L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.check(1L, "read"));

        assertEquals(LEAD_NOT_EXISTS.getCode(), error.getCode());
    }

    @Test
    void readAllowsLeaderOfOwnerDepartment() {
        LeadDO lead = lead(10L, 20L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 101L));
        when(deptApi.getDeptListByLeaderUserId(30L)).thenReturn(List.of(dept(101L)));

        assertReadAllowed(30L);
    }

    @Test
    void readAllowsLeaderOfParentDepartment() {
        LeadDO lead = lead(10L, 20L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 102L));
        when(deptApi.getDeptListByLeaderUserId(30L)).thenReturn(List.of(dept(100L)));
        when(deptApi.getChildDeptList(100L)).thenReturn(List.of(dept(101L), dept(102L)));

        assertReadAllowed(30L);
    }

    @Test
    void readRejectsLeaderOfParallelDepartment() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));
        when(adminUserApi.getUser(20L)).thenReturn(user(20L, 102L));
        when(deptApi.getDeptListByLeaderUserId(30L)).thenReturn(List.of(dept(101L)));
        when(deptApi.getChildDeptList(101L)).thenReturn(List.of());

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(30L);
            ServiceException error = assertThrows(ServiceException.class, () -> service.check(1L, "read"));
            assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        }
    }

    @Test
    void relatedAndManagedUsersAlwaysIncludeCurrentUser() {
        when(deptApi.getDeptListByLeaderUserId(30L)).thenReturn(List.of());

        assertEquals(java.util.Set.of(30L), service.getRelatedAndManagedUserIds(30L));
    }

    @Test
    void activePoolBlocksOwnerEditingAndQualificationButAllowsCollaboratorDealEntry() {
        LeadDO lead = lead(10L, 20L);
        lead.setStatus("valid");
        lead.setAssignmentStatus("owned");
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setLeadId(1L); cycle.setOriginalOwnerUserId(20L); cycle.setCollaboratorUserId(30L);
        cycle.setStatus("assigned");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(agingPoolCycleMapper.selectActiveByLeadId(1L)).thenReturn(cycle);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(20L);
            assertThrows(ServiceException.class, () -> service.check(1L, "basic-info-update"));
            assertThrows(ServiceException.class, () -> service.check(1L, "qualify"));

            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(30L);
            assertDoesNotThrow(() -> service.check(1L, "enter-deal"));
        }
    }

    @Test
    void publicSeaSubmitterAssistOnlyAllowsOwnerAndCollaborator() {
        LeadDO lead = lead(10L, 20L);
        LeadAgingPoolCycleDO cycle = new LeadAgingPoolCycleDO();
        cycle.setLeadId(1L);
        cycle.setOriginalOwnerUserId(20L);
        cycle.setCollaboratorUserId(30L);
        cycle.setStatus("assigned");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(agingPoolCycleMapper.selectActiveByLeadId(1L)).thenReturn(cycle);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(20L);
            assertDoesNotThrow(() -> service.check(1L, "request-submitter-assist"));

            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(30L);
            assertDoesNotThrow(() -> service.check(1L, "request-submitter-assist"));

            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(40L);
            assertThrows(ServiceException.class, () -> service.check(1L, "request-submitter-assist"));
        }
    }
    private void assertReadAllowed(Long userId) {
        assertActionAllowed(userId, "read");
    }

    @Test
    void activeStudentServiceOwnerCanReadHistoryButCannotMutateLead() {
        LeadDO lead = lead(10L, 20L);
        lead.setPersonId(40L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(serviceRelationMapper.countActiveByOwnerAndLead(30L, 1L, 1L)).thenReturn(1L);

        assertActionAllowed(30L, "read");
        assertActionAllowed(30L, "follow-up-read");
        assertActionAllowed(30L, "sales-history-read");
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(30L);
            assertThrows(ServiceException.class, () -> service.check(1L, "follow-up-create"));
            assertThrows(ServiceException.class, () -> service.check(1L, "basic-info-update"));
            assertThrows(ServiceException.class, () -> service.check(1L, "enter-deal"));
        }
    }

    @Test
    void userWithoutActiveStudentServiceRelationCannotReadSalesHistory() {
        LeadDO lead = lead(10L, 20L);
        lead.setPersonId(40L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(serviceRelationMapper.countActiveByOwnerAndLead(30L, 1L, 1L)).thenReturn(0L);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(30L);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.check(1L, "sales-history-read"));
            assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        }
    }

    @Test
    void readRejectsSubmitterDepartmentLeaderWithoutDirectBusinessRelation() {
        when(leadMapper.selectById(1L)).thenReturn(lead(10L, 20L));

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(30L);
            ServiceException error = assertThrows(ServiceException.class, () -> service.check(1L, "read"));
            assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        }
    }

    @Test
    void orderVisibilityIsBoundToRequestedLeadInsteadOfSharedPerson() {
        LeadDO lead = lead(10L, 20L);
        lead.setPersonId(40L);
        SalesOrderDO anotherLeadOrder = new SalesOrderDO();
        anotherLeadOrder.setId(99L); anotherLeadOrder.setLeadId(2L); anotherLeadOrder.setPersonId(40L);
        when(salesOrderMapper.selectByLeadId(1L)).thenReturn(List.of());

        assertFalse(service.canReadDetail(lead, 30L));
        verify(salesOrderObjectPermissionService, org.mockito.Mockito.never()).canRead(anotherLeadOrder, 30L);

        SalesOrderDO requestedLeadOrder = new SalesOrderDO();
        requestedLeadOrder.setId(100L); requestedLeadOrder.setLeadId(1L); requestedLeadOrder.setPersonId(40L);
        when(salesOrderMapper.selectByLeadId(1L)).thenReturn(List.of(requestedLeadOrder));
        when(salesOrderObjectPermissionService.canRead(requestedLeadOrder, 30L)).thenReturn(true);
        assertTrue(service.canReadDetail(lead, 30L));
    }

    @Test
    void mediaAccountParticipantCanReadOnlyTheRelatedLeadDetail() {
        LeadDO lead = lead(10L, 20L);
        when(mediaAccountMapper.countParticipantByLead(30L, 1L, 1L)).thenReturn(1L);

        assertTrue(service.canReadDetail(lead, 30L));
        assertFalse(service.canReadMediaStudentLead(new LeadDO().setId(2L), 30L));
    }

    private void assertActionAllowed(Long userId, String action) {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            assertDoesNotThrow(() -> service.check(1L, action));
        }
    }

    private static LeadDO lead(Long sourceUserId, Long ownerUserId) {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setSourceUserId(sourceUserId);
        if (sourceUserId != null) {
            lead.setProviderOwnerType("system_user");
            lead.setProviderOwnerId(sourceUserId);
        }
        lead.setOwnerUserId(ownerUserId);
        return lead;
    }

    private static AdminUserRespDTO user(Long id, Long deptId) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setDeptId(deptId);
        return user;
    }

    private static DeptRespDTO dept(Long id) {
        DeptRespDTO dept = new DeptRespDTO();
        dept.setId(id);
        return dept;
    }
}
