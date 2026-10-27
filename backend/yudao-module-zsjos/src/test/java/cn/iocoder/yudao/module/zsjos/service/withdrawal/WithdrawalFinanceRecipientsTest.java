package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderApprovalConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WithdrawalFinanceRecipientsTest {
    private final WithdrawalServiceImpl service = new WithdrawalServiceImpl();
    private final SalesOrderObjectPermissionService membership = new SalesOrderObjectPermissionService();
    private final AdminUserApi users = mock(AdminUserApi.class);
    private final DeptApi departments = mock(DeptApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final SalesOrderApprovalConfigMapper config = mock(SalesOrderApprovalConfigMapper.class);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(service, "adminUserApi", users);
        ReflectionTestUtils.setField(service, "permissionApi", permissions);
        ReflectionTestUtils.setField(service, "financeCenterMembership", membership);
        ReflectionTestUtils.setField(membership, "adminUserApi", users);
        ReflectionTestUtils.setField(membership, "deptApi", departments);
        ReflectionTestUtils.setField(membership, "salesOrderApprovalConfigMapper", config);
        when(config.selectCurrent()).thenReturn(new SalesOrderApprovalConfigDO()
                .setFinanceDeptId(1040L).setRegistrationDeptId(1030L));
        when(departments.getChildDeptList(1040L)).thenReturn(List.of(
                new DeptRespDTO().setId(1041L), new DeptRespDTO().setId(1042L)));
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void selectsRootAndAllDescendantsButExcludesOutsideAdminDisabledAndUnpermittedUsers() {
        var root = user(10, 1040, 0);
        var child = user(11, 1041, 0);
        var grandchild = user(12, 1042, 0);
        var outsideAdmin = user(13, 1001, 0);
        var denied = user(14, 1041, 0);
        var disabled = user(15, 1041, 1);
        var noDept = new AdminUserRespDTO().setId(16L).setStatus(0);
        List<AdminUserRespDTO> candidates = List.of(grandchild, outsideAdmin, root, denied, child, disabled, noDept);
        when(users.getUserListByStatus(0)).thenReturn(candidates);
        candidates.forEach(user -> when(users.getUser(user.getId())).thenReturn(user));
        for (long id : new long[]{10, 11, 12, 13, 15}) {
            when(permissions.hasAnyPermissions(id, "zsjos:withdrawal:review")).thenReturn(true);
        }
        assertEquals(List.of(10L, 11L, 12L), ReflectionTestUtils.invokeMethod(service, "financeReviewers"));
        verify(permissions, never()).hasAnyPermissions(13L, "zsjos:withdrawal:review");
        verify(permissions, never()).hasAnyPermissions(15L, "zsjos:withdrawal:review");
    }

    @Test void missingConfigurationDoesNotFallBackToAdministrators() {
        var admin = user(13, 1001, 0);
        when(users.getUserListByStatus(0)).thenReturn(List.of(admin));
        when(users.getUser(13L)).thenReturn(admin);
        when(config.selectCurrent()).thenReturn(null);
        assertEquals(List.of(), ReflectionTestUtils.invokeMethod(service, "financeReviewers"));
        verifyNoInteractions(permissions);
    }

    @Test void emptyEmployeeListProducesNoRecipients() {
        when(users.getUserListByStatus(0)).thenReturn(List.of());
        assertEquals(List.of(), ReflectionTestUtils.invokeMethod(service, "financeReviewers"));
    }

    private AdminUserRespDTO user(long id, long dept, int status) {
        return new AdminUserRespDTO().setId(id).setDeptId(dept).setStatus(status);
    }
}
