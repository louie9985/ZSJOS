package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskRejectHandlerTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmDefinitionReadApiImplTest extends BaseMockitoUnitTest {
    @InjectMocks
    private BpmDefinitionReadApiImpl api;
    @Mock
    private BpmProcessDefinitionService service;

    @Test
    void exposesDefaultEndAndExplicitInternalReturnSemantics() {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn("definition-1");
        when(service.getActiveProcessDefinition("review")).thenReturn(definition);
        BpmnModel model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("review");
        model.addProcess(process);
        UserTask director = new UserTask();
        director.setId("director");
        process.addFlowElement(director);
        UserTask last = new UserTask();
        last.setId("final");
        BpmSimpleModelNodeVO.RejectHandler handler = new BpmSimpleModelNodeVO.RejectHandler();
        handler.setType(BpmUserTaskRejectHandlerTypeEnum.RETURN_USER_TASK.getType());
        handler.setReturnNodeId("director");
        BpmnModelUtils.addTaskRejectElements(handler, last);
        process.addFlowElement(last);
        when(service.getProcessDefinitionBpmnModel("definition-1")).thenReturn(model);

        var tasks = api.getPublishedProcessDefinition("review").getUserTasks();
        assertEquals(2, tasks.size());
        assertTrue(tasks.getFirst().getRejectEndsProcess());
        assertFalse(tasks.getLast().getRejectEndsProcess());
        assertEquals("SINGLE", tasks.getFirst().getExecutionMode());
    }
}
