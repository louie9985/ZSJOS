package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderSupervisorConfirmationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderSupervisorConfirmationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.DISABLE;
import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderObjectPermissionServiceTest {

    @InjectMocks private SalesOrderObjectPermissionService service;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private SalesOrderApprovalConfigMapper approvalConfigMapper;
    @Mock private SalesOrderSupervisorConfirmationMapper supervisorConfirmationMapper;
    @Mock private DeptApi deptApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PermissionApi permissionApi;
    @Mock private cn.iocoder.yudao.module.zsjos.service.lead.LeadAgingPoolService agingPoolService;

    @Test
    void enabledUsersIncludesDepartmentLeadersAndExcludesDisabledUser() {
        DeptRespDTO child = dept(11L, 102L);
        when(deptApi.getChildDeptList(10L)).thenReturn(List.of(child));
        when(adminUserApi.getUserListByDeptIds(Set.of(10L, 11L))).thenReturn(List.of(
                user(101L, ENABLE.getStatus()), user(102L, ENABLE.getStatus()),
                user(103L, ENABLE.getStatus()), user(104L, DISABLE.getStatus())));

        assertEquals(Set.of(101L, 102L, 103L), service.enabledUsers(10L));
    }

    @Test
    void handledSupervisorConfirmationRetainsReadAccess() {
        SalesOrderDO order = new SalesOrderDO().setId(1L).setCurrentApprovalRoundId(20L).setSubmitterUserId(101L);
        SalesOrderSupervisorConfirmationDO confirmation = new SalesOrderSupervisorConfirmationDO()
                .setSupervisorUserId(30L).setStatus("rejected");
        when(supervisorConfirmationMapper.selectByRoundId(20L)).thenReturn(List.of(confirmation));

        org.junit.jupiter.api.Assertions.assertTrue(service.canRead(order, 30L));
        org.junit.jupiter.api.Assertions.assertFalse(service.canRead(new SalesOrderDO()
                .setId(2L).setCurrentApprovalRoundId(21L).setSubmitterUserId(101L), 30L));
        org.junit.jupiter.api.Assertions.assertFalse(service.canRead(order, 31L));
    }

    @Test
    void teamReaderCanReadOnlyOrdersSubmittedByDepartmentTreeMembers() {
        SalesOrderDO order = new SalesOrderDO().setId(1L).setSubmitterUserId(101L);
        AdminUserRespDTO manager = user(30L, ENABLE.getStatus()); manager.setDeptId(10L);
        when(adminUserApi.getUser(30L)).thenReturn(manager);
        when(permissionApi.hasAnyPermissions(30L, "zsjos:sales-order:query-team")).thenReturn(true);
        when(deptApi.getChildDeptList(10L)).thenReturn(List.of(dept(11L, 102L)));
        when(adminUserApi.getUserListByDeptIds(Set.of(10L, 11L))).thenReturn(List.of(user(101L, ENABLE.getStatus())));

        org.junit.jupiter.api.Assertions.assertTrue(service.canRead(order, 30L));
        org.junit.jupiter.api.Assertions.assertFalse(service.canRead(
                new SalesOrderDO().setId(2L).setSubmitterUserId(999L), 30L));
    }

    private DeptRespDTO dept(Long id, Long leaderId) {
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(id); dept.setLeaderUserId(leaderId); return dept;
    }

    private AdminUserRespDTO user(Long id, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(id); user.setStatus(status); return user;
    }
}
