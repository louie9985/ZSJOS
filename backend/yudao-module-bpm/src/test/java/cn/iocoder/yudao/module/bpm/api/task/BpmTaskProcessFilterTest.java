package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BpmTaskProcessFilterTest {
    @Test
    void normalizationPreservesAbsentVersusEmptyAndOpaqueIds() {
        var request = new BpmTaskPageReqDTO();
        assertNull(request.getProcessInstanceIds());
        request.setProcessInstanceIds(Arrays.asList(null, "", " \t", "p1", "p1", " p2 "));
        assertEquals(List.of("p1", " p2 "), request.getProcessInstanceIds());
        request.setProcessInstanceIds(Arrays.asList(null, " "));
        assertEquals(List.of(), request.getProcessInstanceIds());
        request.setProcessInstanceIds(null);
        assertNull(request.getProcessInstanceIds());
    }

    @Test
    void emptyOrAllInvalidFilterNeverCallsUnrestrictedTaskQueries() {
        var api = new BpmProcessTaskApiImpl();
        var service = mock(BpmTaskService.class);
        ReflectionTestUtils.setField(api, "bpmTaskService", service);
        for (var ids : List.of(List.<String>of(), Arrays.asList(null, "", " \n"))) {
            var request = new BpmTaskPageReqDTO();
            request.setProcessInstanceIds(ids);
            assertEquals(0L, api.getTodoTaskPage(1L, request).getTotal());
            assertEquals(0L, api.getDoneTaskPage(1L, request).getTotal());
        }
        verifyNoInteractions(service);
    }

    @Test
    void bothTaskQueriesReceiveOnlyDistinctValidIdsAndAbsentFilterStaysAbsent() {
        var api = new BpmProcessTaskApiImpl();
        var service = mock(BpmTaskService.class);
        ReflectionTestUtils.setField(api, "bpmTaskService", service);
        when(service.getTaskTodoPage(eq(1L), any(), any(), any())).thenReturn(PageResult.empty());
        when(service.getTaskDonePage(eq(1L), any(), any(), any())).thenReturn(PageResult.empty());
        var request = new BpmTaskPageReqDTO();
        request.setProcessInstanceIds(Arrays.asList(null, "", "p1", "p1"));
        api.getTodoTaskPage(1L, request);
        api.getDoneTaskPage(1L, request);
        var todo = ArgumentCaptor.forClass(BpmTaskPageReqVO.class);
        var done = ArgumentCaptor.forClass(BpmTaskPageReqVO.class);
        verify(service).getTaskTodoPage(eq(1L), todo.capture(), any(), any());
        verify(service).getTaskDonePage(eq(1L), done.capture(), any(), any());
        assertEquals(List.of("p1"), todo.getValue().getProcessInstanceIds());
        assertEquals(List.of("p1"), done.getValue().getProcessInstanceIds());
        request.setProcessInstanceIds(null);
        api.getTodoTaskPage(1L, request);
        verify(service, times(2)).getTaskTodoPage(eq(1L), todo.capture(), any(), any());
        assertNull(todo.getValue().getProcessInstanceIds());
    }
}
