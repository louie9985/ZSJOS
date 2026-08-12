package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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
    @Mock private LeadAssignmentService leadAssignmentService;

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

    private void assertReadAllowed(Long userId) {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(userId);
            assertDoesNotThrow(() -> service.check(1L, "read"));
        }
    }

    private static LeadDO lead(Long sourceUserId, Long ownerUserId) {
        LeadDO lead = new LeadDO();
        lead.setId(1L);
        lead.setSourceUserId(sourceUserId);
        lead.setOwnerUserId(ownerUserId);
        return lead;
    }
}
