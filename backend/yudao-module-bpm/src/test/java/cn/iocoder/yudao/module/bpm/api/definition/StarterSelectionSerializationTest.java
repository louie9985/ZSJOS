package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.util.io.BytesStreamSource;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StarterSelectionSerializationTest {
    @Test void roleRangeSurvivesSimpleJsonBpmnXmlRoundTrip() throws Exception {
        var root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("script/bpm")))root=root.getParent();
        var json=JsonUtils.parseTree(Files.readString(root.resolve("script/bpm/zsjos_points_redemption/1.0.0/process-model.json")));
        var node=JsonUtils.parseObject(json.path("simpleModel").toString(),BpmSimpleModelNodeVO.class);
        node.getChildNode().setCandidateStrategy(35);node.getChildNode().setCandidateParam("7,9");
        node=JsonUtils.parseObject(JsonUtils.toJsonString(node),BpmSimpleModelNodeVO.class);
        var model=SimpleModelUtils.buildBpmnModel("role_selection","角色自选",node);
        var xml=new BpmnXMLConverter();model=xml.convertToBpmnModel(new BytesStreamSource(xml.convertToXML(model)),false,false);
        var task=(UserTask)model.getMainProcess().getFlowElement(node.getChildNode().getId());
        assertEquals(35,BpmnModelUtils.parseCandidateStrategy(task));
        assertEquals("7,9",BpmnModelUtils.parseCandidateParam(task));
    }
}
