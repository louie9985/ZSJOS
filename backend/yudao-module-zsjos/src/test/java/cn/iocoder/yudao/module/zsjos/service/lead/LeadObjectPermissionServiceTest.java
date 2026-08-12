package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
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
