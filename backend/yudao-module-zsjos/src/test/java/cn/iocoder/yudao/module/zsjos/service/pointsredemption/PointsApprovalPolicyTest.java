package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.bpm.api.definition.dto.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PointsApprovalPolicyTest {
    @Test void acceptsMandatoryChain() {assertDoesNotThrow(()->PointsApprovalPolicy.validateStructure(definition()));}
    @Test void rejectsMissingOrBypassedFinalApproval() {
        assertThrows(RuntimeException.class,()->PointsApprovalPolicy.validateStructure(null));
        var d=definition();d.setMandatoryHumanChain(false);assertThrows(RuntimeException.class,()->PointsApprovalPolicy.validateStructure(d));
        var invalid=definition();invalid.getUserTasks().getLast().setMandatoryHumanApproval(false);
        assertThrows(RuntimeException.class,()->PointsApprovalPolicy.validateStructure(invalid));
    }
    @Test void rejectsWrongCandidatesAndOneLevel() {
        var d=definition();d.getUserTasks().getFirst().setCandidateStrategy(30);
        assertThrows(RuntimeException.class,()->PointsApprovalPolicy.validateStructure(d));
        var one=definition();one.setOrderedUserTaskKeys(List.of("final"));
        assertThrows(RuntimeException.class,()->PointsApprovalPolicy.validateStructure(one));
    }
    static BpmProcessDefinitionMetadataRespDTO definition() {
        var d=new BpmProcessDefinitionMetadataRespDTO();d.setMandatoryHumanChain(true);d.setOrderedUserTaskKeys(List.of("supervisor","final"));
        d.setUserTasks(List.of(task("supervisor",37),task("final",30)));return d;
    }
    static BpmUserTaskMetadataRespDTO task(String key,int strategy) {
        var t=new BpmUserTaskMetadataRespDTO();t.setKey(key);t.setCandidateStrategy(strategy);t.setCandidateParam(strategy==37?"1":"9");t.setExecutionMode("SINGLE");
        t.setMandatoryHumanApproval(true);t.setRejectEndsProcess(true);return t;
    }
}
