package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BpmTaskNotificationTargetTest {
    @InjectMocks private BpmTaskController controller;
    @Mock private BpmTaskService service;

    @Test void keepsExactTaskAndRejectsCompletionRaceFallback() {
        try (var security = mockStatic(WebFrameworkUtils.class)) {
            security.when(WebFrameworkUtils::getLoginUserId).thenReturn(11L);
            Task task = mock(Task.class);
            when(service.getTask("requested")).thenReturn(task);
            when(task.getAssignee()).thenReturn("11");
            when(task.getProcessInstanceId()).thenReturn("process");
            when(task.getTaskLocalVariables()).thenReturn(Map.of(BpmnVariableConstants.TASK_VARIABLE_STATUS, 1));
            var exact = new BpmTaskRespVO().setId("requested").setStatus(1);
            when(service.getTodoTask(11L, "requested", "process")).thenReturn(exact);
            assertSame(exact, controller.getTodo("requested").getData());
            when(service.getTodoTask(11L, "requested", "process"))
                    .thenReturn(new BpmTaskRespVO().setId("another-task").setStatus(1));
            assertThrows(ServiceException.class, () -> controller.getTodo("requested"));
            when(service.getTodoTask(11L, "requested", "process")).thenReturn(null);
            assertThrows(ServiceException.class, () -> controller.getTodo("requested"));
        }
    }

    @Test void rejectsTransferredAndMissingTasks() {
        try (var security = mockStatic(WebFrameworkUtils.class)) {
            security.when(WebFrameworkUtils::getLoginUserId).thenReturn(11L);
            Task task = mock(Task.class);
            when(service.getTask("transferred")).thenReturn(task);
            when(task.getAssignee()).thenReturn("22");
            assertThrows(ServiceException.class, () -> controller.getTodo("transferred"));
            assertThrows(ServiceException.class, () -> controller.getTodo("missing"));
            verify(service, never()).getTodoTask(any(), any(), any());
        }
    }
}
