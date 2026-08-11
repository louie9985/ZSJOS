package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BpmProcessTaskApiImplTest extends BaseMockitoUnitTest {

    private static final Long USER_ID = 233L;

    @InjectMocks
    private BpmProcessTaskApiImpl processTaskApi;

    @Mock
    private BpmTaskService bpmTaskService;
    @Mock
    private BpmProcessInstanceService processInstanceService;

    @Test
    void getTodoTaskPageReturnsEmptyPageWithoutQueryingProcessInstances() {
        BpmTaskPageReqDTO reqDTO = pageReq();
        when(bpmTaskService.getTaskTodoPage(eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.empty());

        PageResult<BpmTaskRespDTO> result = processTaskApi.getTodoTaskPage(USER_ID, reqDTO);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verifyNoInteractions(processInstanceService);
    }

    @Test
    void getDoneTaskPageReturnsEmptyPageWithoutQueryingProcessInstances() {
        BpmTaskPageReqDTO reqDTO = pageReq();
        when(bpmTaskService.getTaskDonePage(eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.empty());

        PageResult<BpmTaskRespDTO> result = processTaskApi.getDoneTaskPage(USER_ID, reqDTO);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verifyNoInteractions(processInstanceService);
    }

    private BpmTaskPageReqDTO pageReq() {
        BpmTaskPageReqDTO reqDTO = new BpmTaskPageReqDTO();
        reqDTO.setPageNo(1);
        reqDTO.setPageSize(10);
        reqDTO.setProcessDefinitionKey("zsjos_lead_appeal_review");
        return reqDTO;
    }
}
