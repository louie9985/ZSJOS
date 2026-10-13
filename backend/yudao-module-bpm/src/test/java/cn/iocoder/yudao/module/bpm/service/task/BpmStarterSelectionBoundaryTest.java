package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.*;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.bpmn.model.*;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BpmStarterSelectionBoundaryTest {
    @Test void genericAdminStartAlsoRejectsForgedRoleSelection() {
        var service=spy(new BpmProcessInstanceServiceImpl());
        var models=mock(BpmModelService.class);var policy=mock(BpmStarterSelectionPolicy.class);
        var users=mock(AdminUserApi.class);var definition=mock(ProcessDefinition.class);
        ReflectionTestUtils.setField(service,"modelService",models);ReflectionTestUtils.setField(service,"starterSelectionPolicy",policy);
        ReflectionTestUtils.setField(service,"adminUserApi",users);
        when(definition.getId()).thenReturn("d1");
        var task=new UserTask();task.setId("review");task.setName("编导审核");
        BpmnModelUtils.addExtensionElement(task,"candidateStrategy","35");BpmnModelUtils.addExtensionElement(task,"candidateParam","7");
        var model=new BpmnModel();var process=new org.flowable.bpmn.model.Process();process.setId("test");process.addFlowElement(task);model.addProcess(process);
        when(models.getBpmnModelByDefinitionId("d1")).thenReturn(model);
        doAnswer(inv->{var detail=new BpmApprovalDetailRespVO();var node=new BpmApprovalDetailRespVO.ActivityNode();node.setId("review");node.setName("编导审核");node.setCandidateStrategy(35);detail.setActivityNodes(new ArrayList<>(List.of(node)));return detail;})
                .when(service).getApprovalDetail(eq(1L),any(BpmApprovalDetailReqVO.class));
        doThrow(new IllegalArgumentException("outside scope")).when(policy).validate("7",List.of(99L));
        assertThrows(IllegalArgumentException.class,()->ReflectionTestUtils.invokeMethod(service,"validateStartUserSelectAssignees",1L,definition,Map.of("review",List.of(99L)),Map.of()));
        verifyNoInteractions(users);
        when(users.getUserMap(List.of(2L))).thenReturn(Map.of(2L,new AdminUserRespDTO().setId(2L).setStatus(0)));
        assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(service,"validateStartUserSelectAssignees",1L,definition,Map.of("review",List.of(2L)),Map.of()));
        verify(policy).validate("7",List.of(2L));
    }
}
