package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmPendingTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadAppealHandleAccessTest {
    @InjectMocks private LeadAppealServiceImpl service;
    @Mock private LeadAppealMapper mapper;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi users;
    @Mock private BpmProcessTaskApi tasks;
    private LeadAppealDO setup(String status) {
        var a = new LeadAppealDO(); a.setId(1L); a.setRoundNo(1); a.setStatus(status); a.setReviewStage(APPEAL_STAGE_SALES_MANAGER);
        a.setProcessInstanceId("p1"); a.setReviewerUserIdsSnapshot("[9]");
        when(mapper.selectById(1L)).thenReturn(a); return a;
    }
    private void allow() {
        when(permissionApi.hasAnyPermissions(9L, PERMISSION_APPEAL_REVIEW_SALES_MANAGER)).thenReturn(true);
        var u = new AdminUserRespDTO(); u.setId(9L); u.setStatus(0); when(users.getUser(9L)).thenReturn(u);
    }
    @Test void deniesFinishedAndMissingPermission() {
        setup("upheld"); assertFalse(service.canHandleAppeal(1L, 9L)); verifyNoInteractions(tasks, permissionApi);
        setup("sales_manager_reviewing"); assertFalse(service.canHandleAppeal(1L, 9L)); verifyNoInteractions(tasks);
    }
    @Test void requiresAssignedTaskAndMatchingProcessAndBusiness() {
        setup("sales_manager_reviewing"); allow();
        when(tasks.getPendingTasks("p1")).thenReturn(List.of(new BpmPendingTaskRespDTO("t", APPEAL_TASK_DEFINITION_KEY, 8L, null)),
                List.of(new BpmPendingTaskRespDTO("t", APPEAL_TASK_DEFINITION_KEY, 9L, null)));
        assertFalse(service.canHandleAppeal(1L, 9L));
        var t = new BpmTaskRespDTO(); t.setId("t"); t.setProcessInstanceId("p1"); t.setBusinessKey(APPEAL_BUSINESS_KEY_PREFIX + "1");
        when(tasks.getTodoTask(9L, "t")).thenReturn(t);
        assertTrue(service.canHandleAppeal(1L, 9L));
        t.setProcessInstanceId("other"); assertFalse(service.canHandleAppeal(1L, 9L));
    }
    @Test void deniesReviewerSnapshotMismatch() {
        setup("sales_manager_reviewing").setReviewerUserIdsSnapshot("[8]"); allow();
        assertFalse(service.canHandleAppeal(1L, 9L)); verifyNoInteractions(tasks);
    }
}
