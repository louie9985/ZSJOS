package cn.iocoder.yudao.module.bpm.api.definition;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PointsDefinitionSafetyTest {
    @Test void actualSimpleTemplateCompilesToMandatoryHumanChain() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("script/bpm")))root=root.getParent();
        var json=JsonUtils.parseTree(Files.readString(root.resolve("script/bpm/zsjos_points_redemption/1.0.0/process-model.json")));
        var node=JsonUtils.parseObject(json.path("simpleModel").toString(),BpmSimpleModelNodeVO.class);
        var model=SimpleModelUtils.buildBpmnModel("zsjos_points_redemption","积分审批",node);
        var service=mock(BpmProcessDefinitionService.class);var definition=mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn("d1");when(service.getActiveProcessDefinition("points")).thenReturn(definition);
        when(service.getProcessDefinitionBpmnModel("d1")).thenReturn(model);
        var info=new BpmProcessDefinitionInfoDO();info.setAutoApprovalType(0);when(service.getProcessDefinitionInfo("d1")).thenReturn(info);
        var api=new BpmDefinitionReadApiImpl();ReflectionTestUtils.setField(api,"processDefinitionService",service);
        var result=api.getPublishedProcessDefinition("points");assertTrue(result.getMandatoryHumanChain());
        assertEquals(java.util.List.of("StartUserNode","supervisorReview","chairmanReview"),result.getOrderedUserTaskKeys());
        result.getUserTasks().stream().filter(t->!"StartUserNode".equals(t.getKey())).forEach(t->{assertTrue(t.getMandatoryHumanApproval());assertEquals("SINGLE",t.getExecutionMode());});
        info.setAutoApprovalType(1);assertFalse(api.getPublishedProcessDefinition("points").getMandatoryHumanChain());
        model.getMainProcess().findFlowElementsOfType(org.flowable.bpmn.model.SequenceFlow.class).getFirst().setConditionExpression("${true}");
        info.setAutoApprovalType(0);assertFalse(api.getPublishedProcessDefinition("points").getMandatoryHumanChain());
    }
}
