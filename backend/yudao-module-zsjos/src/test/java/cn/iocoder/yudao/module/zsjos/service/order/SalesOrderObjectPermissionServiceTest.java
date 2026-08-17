package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
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

    private DeptRespDTO dept(Long id, Long leaderId) {
        DeptRespDTO dept = new DeptRespDTO(); dept.setId(id); dept.setLeaderUserId(leaderId); return dept;
    }

    private AdminUserRespDTO user(Long id, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO(); user.setId(id); user.setStatus(status); return user;
    }
}
