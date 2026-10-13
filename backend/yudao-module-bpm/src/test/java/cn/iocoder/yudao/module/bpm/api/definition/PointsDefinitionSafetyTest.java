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
import java.util.List;
import org.flowable.bpmn.model.*;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.common.engine.impl.util.io.BytesStreamSource;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PointsDefinitionSafetyTest {
    @Test void supportsConsecutiveDeduplicationWithoutChangingStrictManualMetadata() throws Exception {
        var model = roundTrip(reminderModel());
        var result = metadata(model, 2);
        assertFalse(result.getMandatoryHumanChain());
        assertTrue(result.getConsecutiveApprovalDeduplicationChain());
        assertEquals(List.of("StartUserNode", "supervisorReview", "chairmanReview"), result.getOrderedUserTaskKeys());
        assertFalse(metadata(model, 1).getConsecutiveApprovalDeduplicationChain());
        assertFalse(metadata(model, 0).getConsecutiveApprovalDeduplicationChain());
        model.getMainProcess().findFlowElementsOfType(SequenceFlow.class).getFirst().setConditionExpression("${true}");
        assertFalse(metadata(roundTrip(model), 2).getConsecutiveApprovalDeduplicationChain());
    }

    @Test void acceptsSerializedReminderTimersWithoutChangingLegacySequentialFlag() throws Exception {
        var model = roundTrip(reminderModel());
        assertEquals(2, model.getMainProcess().findFlowElementsOfType(BoundaryEvent.class).size());
        var result = metadata(model, 0);
        assertTrue(result.getMandatoryHumanChain());
        assertFalse(result.getSimpleSequentialApproval());
        assertEquals(List.of("StartUserNode", "supervisorReview", "chairmanReview"), result.getOrderedUserTaskKeys());
        result.getUserTasks().stream().filter(t -> !"StartUserNode".equals(t.getKey())).forEach(t -> {
            assertTrue(t.getMandatoryHumanApproval());
            assertEquals("SINGLE", t.getExecutionMode());
        });
    }

    @Test void rejectsTimeoutAutoApproveAndAutoReject() throws Exception {
        for (String handler : List.of("2", "3")) {
            var model = reminderModel();
            reminder(model).getExtensionElements().get("timeoutHandlerType").getFirst().setElementText(handler);
            assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain(), "timeout handler " + handler);
        }
    }

    @Test void rejectsInterruptingTimer() throws Exception {
        var model = reminderModel();
        reminder(model).setCancelActivity(true);
        assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
    }

    @Test void rejectsReminderWithOutgoingBypassOrIncomingFlow() throws Exception {
        for (boolean outgoing : List.of(true, false)) {
            var model = reminderModel();
            var event = reminder(model);
            var flow = new SequenceFlow(outgoing ? event.getId() : "StartUserNode",
                    outgoing ? "EndEvent" : event.getId());
            flow.setId("unsafeReminderFlow");
            model.getMainProcess().addFlowElement(flow);
            assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
        }
    }

    @Test void rejectsNonTimerAndUnknownBoundaryHandler() throws Exception {
        var model = reminderModel();
        reminder(model).setEventDefinitions(List.of(new SignalEventDefinition()));
        assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
        model = reminderModel();
        reminder(model).getExtensionElements().get("boundaryEventType").getFirst().setElementText("2");
        assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
    }

    @Test void rejectsReminderAttachedToSubmissionInsteadOfHumanReview() throws Exception {
        var model = reminderModel();
        reminder(model).setAttachedToRef((UserTask) model.getMainProcess().getFlowElement("StartUserNode"));
        assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
    }

    @Test void rejectsReminderExecutionListener() throws Exception {
        var model = reminderModel();
        var listener = new FlowableListener();
        listener.setEvent("start");
        listener.setImplementationType("expression");
        listener.setImplementation("${true}");
        reminder(model).getExecutionListeners().add(listener);
        assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
    }

    @Test void remindersDoNotAllowAutomaticApprovalOrConditionalPaths() throws Exception {
        var model = reminderModel();
        assertFalse(metadata(roundTrip(model), 1).getMandatoryHumanChain());
        model.getMainProcess().findFlowElementsOfType(SequenceFlow.class).getFirst().setConditionExpression("${true}");
        assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
    }

    @Test void remindersDoNotAllowSkippingTheirHumanTask() throws Exception {
        var model = reminderModel();
        ((UserTask) model.getMainProcess().getFlowElement("supervisorReview")).setSkipExpression("${true}");
        assertFalse(metadata(roundTrip(model), 0).getMandatoryHumanChain());
    }

    private BpmnModel reminderModel() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("script/bpm"))) root = root.getParent();
        var json = JsonUtils.parseTree(Files.readString(root.resolve("script/bpm/zsjos_points_redemption/1.0.0/process-model.json")));
        var node = JsonUtils.parseObject(json.path("simpleModel").toString(), BpmSimpleModelNodeVO.class);
        for (var task : List.of(node.getChildNode(), node.getChildNode().getChildNode())) {
            var timeout = new BpmSimpleModelNodeVO.TimeoutHandler();
            timeout.setEnable(true);
            timeout.setType(1);
            timeout.setTimeDuration("PT6H");
            timeout.setMaxRemindCount(3);
            task.setTimeoutHandler(timeout);
        }
        return SimpleModelUtils.buildBpmnModel("zsjos_points_redemption", "积分审批", node);
    }

    private BoundaryEvent reminder(BpmnModel model) {
        return model.getMainProcess().findFlowElementsOfType(BoundaryEvent.class).getFirst();
    }

    private BpmnModel roundTrip(BpmnModel model) {
        var converter = new BpmnXMLConverter();
        return converter.convertToBpmnModel(new BytesStreamSource(converter.convertToXML(model)), false, false);
    }

    private BpmProcessDefinitionMetadataRespDTO metadata(BpmnModel model, int autoApprovalType) {
        var service = mock(BpmProcessDefinitionService.class);
        var definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn("d1");
        when(service.getActiveProcessDefinition("points")).thenReturn(definition);
        when(service.getProcessDefinitionBpmnModel("d1")).thenReturn(model);
        var info = new BpmProcessDefinitionInfoDO();
        info.setAutoApprovalType(autoApprovalType);
        when(service.getProcessDefinitionInfo("d1")).thenReturn(info);
        var api = new BpmDefinitionReadApiImpl();
        ReflectionTestUtils.setField(api, "processDefinitionService", service);
        return api.getPublishedProcessDefinition("points");
    }

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
