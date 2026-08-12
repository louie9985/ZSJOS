package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkPlanMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.WorkTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkPlanObjectPermissionProviderTest {
    @InjectMocks private WorkPlanObjectPermissionProvider provider;
    @Mock private WorkPlanMapper planMapper;
    @Mock private WorkTaskMapper taskMapper;
    @Mock private PermissionApi permissionApi;

    @Test void taskAssigneeCanReportButNotConfirm() {
        WorkTaskDO task = new WorkTaskDO().setId(1L).setPlanId(2L).setAssigneeUserId(10L).setConfirmerUserId(20L).setAssigneeDeptId(30L).setStatus("pending");
        when(planMapper.selectById(2L)).thenReturn(new WorkPlanDO().setId(2L));
        assertTrue(provider.hasTaskPermission(task, "report", 10L)); assertFalse(provider.hasTaskPermission(task, "confirm", 10L)); assertTrue(provider.hasTaskPermission(task, "confirm", 20L));
    }

    @Test void departmentScopeCanReadPlan() {
        WorkPlanDO plan = new WorkPlanDO().setId(2L).setOwnerDeptId(30L);
        when(planMapper.selectById(2L)).thenReturn(plan);
        DeptDataPermissionRespDTO scope = new DeptDataPermissionRespDTO().setAll(false).setDeptIds(Set.of(30L)); when(permissionApi.getDeptDataPermission(10L)).thenReturn(scope);
        assertTrue(provider.hasPermission(2L, "read", 10L));
    }

    @Test void unfinishedChildrenDoNotHideReportAction() {
        WorkTaskDO task = new WorkTaskDO().setId(1L).setPlanId(2L).setAssigneeUserId(10L)
                .setStatus("pending");
        when(planMapper.selectById(2L)).thenReturn(new WorkPlanDO().setId(2L));
        when(permissionApi.hasAnyPermissions(eq(10L), anyString()))
                .thenAnswer(invocation -> "zsjos:work-plan:complete".equals(invocation.getArgument(1)));

        assertTrue(provider.availableTaskActions(task, true, 10L).contains("complete"));
    }

    @Test void activePlanCanBeSummarizedBeforeAllTasksFinish() {
        WorkPlanDO plan = new WorkPlanDO().setId(2L).setOwnerUserId(10L).setStatus("active");
        when(planMapper.selectById(2L)).thenReturn(plan);
        when(permissionApi.hasAnyPermissions(eq(10L), anyString()))
                .thenAnswer(invocation -> "zsjos:work-plan:close".equals(invocation.getArgument(1)));

        assertTrue(provider.availablePlanActions(plan, false, 10L).contains("close"));
    }
}
