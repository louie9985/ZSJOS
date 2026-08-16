package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskSignReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskSignCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessNodeStatusRespDTO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.TASK_VARIABLE_STATUS;

class BpmProcessTaskApiImplTest extends BaseMockitoUnitTest {

    private static final Long USER_ID = 233L;

    @InjectMocks
    private BpmProcessTaskApiImpl processTaskApi;

    @Mock
    private BpmTaskService bpmTaskService;
    @Mock
    private BpmProcessInstanceService processInstanceService;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    void getTodoTaskPageReturnsEmptyPageWithoutQueryingProcessInstances() {
        BpmTaskPageReqDTO reqDTO = pageReq();
        reqDTO.setProcessVariableName("reviewStage");
        reqDTO.setProcessVariableValues(List.of("sales_manager", "quality"));
        when(bpmTaskService.getTaskTodoPage(eq(USER_ID), org.mockito.ArgumentMatchers.any(),
                eq("reviewStage"), eq(List.of("sales_manager", "quality"))))
                .thenReturn(PageResult.empty());

        PageResult<BpmTaskRespDTO> result = processTaskApi.getTodoTaskPage(USER_ID, reqDTO);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        ArgumentCaptor<BpmTaskPageReqVO> request = ArgumentCaptor.forClass(BpmTaskPageReqVO.class);
        verify(bpmTaskService).getTaskTodoPage(eq(USER_ID), request.capture(),
                eq("reviewStage"), eq(List.of("sales_manager", "quality")));
        assertEquals("zsjos_lead_appeal_review", request.getValue().getProcessDefinitionKey());
        verifyNoInteractions(processInstanceService);
    }

    @Test
    void getDoneTaskPageReturnsEmptyPageWithoutQueryingProcessInstances() {
        BpmTaskPageReqDTO reqDTO = pageReq();
        when(bpmTaskService.getTaskDonePage(eq(USER_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(PageResult.empty());

        PageResult<BpmTaskRespDTO> result = processTaskApi.getDoneTaskPage(USER_ID, reqDTO);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verifyNoInteractions(processInstanceService);
    }

    @Test
    void getProcessNodeStatusesKeepsApprovedDecisionWhenSiblingWasCancelledLater() {
        HistoricTaskInstance approved = historicTask("registrationReview", 2, 1000L, "233");
        HistoricTaskInstance cancelled = historicTask("registrationReview", 4, 2000L);
        when(bpmTaskService.getTasksByProcessInstanceIds(List.of("process-1"))).thenReturn(List.of());
        when(bpmTaskService.getTaskListByProcessInstanceIds(Set.of("process-1"))).thenReturn(List.of(approved, cancelled));
        AdminUserRespDTO reviewer = new AdminUserRespDTO();
        reviewer.setId(233L); reviewer.setNickname("审核员甲");
        when(adminUserApi.getUserMap(Set.of(233L))).thenReturn(Map.of(233L, reviewer));

        List<BpmProcessNodeStatusRespDTO> result = processTaskApi.getProcessNodeStatuses(
                "process-1", Set.of("registrationReview", "financeReview"));

        assertEquals(1, result.size());
        assertEquals("approved", result.getFirst().getStatus());
        assertEquals(233L, result.getFirst().getReviewerUserId());
        assertEquals("审核员甲", result.getFirst().getReviewerUserName());
    }

    @Test
    void getProcessNodeStatusesReportsRunningNodeAsPending() {
        Task running = mock(Task.class);
        when(running.getProcessInstanceId()).thenReturn("process-1");
        when(running.getTaskDefinitionKey()).thenReturn("financeReview");
        when(running.getCreateTime()).thenReturn(new Date(1000L));
        when(bpmTaskService.getTasksByProcessInstanceIds(List.of("process-1"))).thenReturn(List.of(running));
        when(bpmTaskService.getTaskListByProcessInstanceIds(Set.of("process-1"))).thenReturn(List.of());

        List<BpmProcessNodeStatusRespDTO> result = processTaskApi.getProcessNodeStatuses(
                "process-1", Set.of("registrationReview", "financeReview"));

        assertEquals("pending", result.getFirst().getStatus());
    }

    @Test
    void getProcessNodeStatusesIgnoresSignChildren() {
        Task supervisorTask = mock(Task.class);
        when(supervisorTask.getTaskDefinitionKey()).thenReturn("registrationReview");
        when(supervisorTask.getParentTaskId()).thenReturn("parent-task");
        HistoricTaskInstance historicSupervisorTask = mock(HistoricTaskInstance.class);
        when(historicSupervisorTask.getTaskDefinitionKey()).thenReturn("financeReview");
        when(historicSupervisorTask.getParentTaskId()).thenReturn("historic-parent");
        when(bpmTaskService.getTasksByProcessInstanceIds(List.of("process-1")))
                .thenReturn(List.of(supervisorTask));
        when(bpmTaskService.getTaskListByProcessInstanceIds(Set.of("process-1")))
                .thenReturn(List.of(historicSupervisorTask));

        List<BpmProcessNodeStatusRespDTO> result = processTaskApi.getProcessNodeStatuses(
                "process-1", Set.of("registrationReview", "financeReview"));

        assertTrue(result.isEmpty());
        verifyNoInteractions(adminUserApi);
    }

    @Test
    void createBeforeSignTaskReturnsCreatedChildTaskId() {
        when(bpmTaskService.createSignTask(eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("child-task-1"));
        BpmTaskSignReqDTO request = new BpmTaskSignReqDTO();
        request.setTaskId("parent-task"); request.setAssigneeUserId(300L); request.setReason("需要主管确认");

        String result = processTaskApi.createBeforeSignTask(USER_ID, request);

        assertEquals("child-task-1", result);
        verify(bpmTaskService).createSignTask(eq(USER_ID), org.mockito.ArgumentMatchers.<BpmTaskSignCreateReqVO>argThat(value ->
                "parent-task".equals(value.getId()) && value.getUserIds().equals(Set.of(300L))
                        && "需要主管确认".equals(value.getReason())));
    }

    @Test
    void getProcessNodeStatusesGroupsMultipleInstancesInOneBatch() {
        Task first = mock(Task.class); when(first.getProcessInstanceId()).thenReturn("process-1");
        when(first.getTaskDefinitionKey()).thenReturn("registrationReview");
        Task second = mock(Task.class); when(second.getProcessInstanceId()).thenReturn("process-2");
        when(second.getTaskDefinitionKey()).thenReturn("financeReview");
        when(bpmTaskService.getTasksByProcessInstanceIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(first, second));
        when(bpmTaskService.getTaskListByProcessInstanceIds(Set.of("process-1", "process-2"))).thenReturn(List.of());

        Map<String, List<BpmProcessNodeStatusRespDTO>> result = processTaskApi.getProcessNodeStatuses(
                Set.of("process-1", "process-2"), Set.of("registrationReview", "financeReview"));

        assertEquals("registrationReview", result.get("process-1").getFirst().getTaskDefinitionKey());
        assertEquals("financeReview", result.get("process-2").getFirst().getTaskDefinitionKey());
        verify(bpmTaskService).getTasksByProcessInstanceIds(org.mockito.ArgumentMatchers.anyList());
        verify(bpmTaskService).getTaskListByProcessInstanceIds(Set.of("process-1", "process-2"));
    }

    private HistoricTaskInstance historicTask(String taskKey, int status, long endTime) {
        return historicTask(taskKey, status, endTime, null);
    }

    private HistoricTaskInstance historicTask(String taskKey, int status, long endTime, String assignee) {
        HistoricTaskInstance task = mock(HistoricTaskInstance.class);
        when(task.getProcessInstanceId()).thenReturn("process-1");
        when(task.getTaskDefinitionKey()).thenReturn(taskKey);
        org.mockito.Mockito.lenient().when(task.getAssignee()).thenReturn(assignee);
        org.mockito.Mockito.lenient().when(task.getCreateTime()).thenReturn(new Date(500L));
        when(task.getEndTime()).thenReturn(new Date(endTime));
        when(task.getTaskLocalVariables()).thenReturn(Map.of(TASK_VARIABLE_STATUS, status));
        return task;
    }

    private BpmTaskPageReqDTO pageReq() {
        BpmTaskPageReqDTO reqDTO = new BpmTaskPageReqDTO();
        reqDTO.setPageNo(1);
        reqDTO.setPageSize(10);
        reqDTO.setProcessDefinitionKey("zsjos_lead_appeal_review");
        return reqDTO;
    }
}
