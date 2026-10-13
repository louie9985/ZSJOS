package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.bpm.api.definition.dto.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PointsApprovalPolicyTest {
    @Test void acceptsConfiguredConsecutiveDeduplicationWithSameApprover() {
        var d = definition(); d.setMandatoryHumanChain(false); d.setConsecutiveApprovalDeduplicationChain(true);
        assertDoesNotThrow(() -> PointsApprovalPolicy.validateStructure(d));
        d.getUserTasks().getLast().setCandidateParam("8");
        var policy = policy(d, mock(AdminUserApi.class));
        assertSame(d, policy.validate(7L));
        d.getUserTasks().getFirst().setMandatoryHumanApproval(false);
        assertThrows(ServiceException.class, () -> policy.validate(7L));
    }

    @Test void distinguishesMissingStructureAndNodeErrors() {
        assertEquals(1_090_296_005, assertThrows(ServiceException.class,
                () -> PointsApprovalPolicy.validateStructure(null)).getCode());
        var suspended = definition(); suspended.setSuspended(true);
        assertEquals(1_090_296_005, assertThrows(ServiceException.class,
                () -> PointsApprovalPolicy.validateStructure(suspended)).getCode());
        var invalidChain = definition(); invalidChain.setMandatoryHumanChain(false);
        assertEquals(1_090_296_006, assertThrows(ServiceException.class,
                () -> PointsApprovalPolicy.validateStructure(invalidChain)).getCode());
        var invalidNode = definition(); invalidNode.getUserTasks().getFirst().setMandatoryHumanApproval(false);
        var error = assertThrows(ServiceException.class, () -> PointsApprovalPolicy.validateStructure(invalidNode));
        assertEquals(1_090_296_004, error.getCode());
        assertTrue(error.getMessage().contains("禁止同人自动跳过"));
        invalidNode.getUserTasks().getFirst().setMandatoryHumanApproval(true);
        invalidNode.getUserTasks().getFirst().setExecutionMode("SEQUENTIAL_MULTI_INSTANCE");
        assertTrue(assertThrows(ServiceException.class, () -> PointsApprovalPolicy.validateStructure(invalidNode))
                .getMessage().contains("单人处理"));
    }

    @Test void validatesSingleApproverAndReportsSelfApprovalExplicitly() {
        var d = definition();
        var users = mock(AdminUserApi.class);
        var policy = policy(d, users);
        assertSame(d, policy.validate(7L));
        verify(users).validateUserList(List.of(9L));
        d.getUserTasks().getLast().setCandidateParam("7");
        var error = assertThrows(ServiceException.class, () -> policy.validate(7L));
        assertEquals(1_090_296_007, error.getCode());
        assertTrue(error.getMessage().contains("申请人不能同时担任"));
    }

    @Test void rejectsMissingMultipleOrDisabledApproversWithoutMisreportingPublication() {
        for (String ids : Arrays.asList(null, "", "bad", "9,", "9,10")) {
            var d = definition(); d.getUserTasks().getLast().setCandidateParam(ids);
            var policy = policy(d, mock(AdminUserApi.class));
            var error = assertThrows(ServiceException.class, () -> policy.validate(7L));
            assertEquals(1_090_296_007, error.getCode());
        }
        var users = mock(AdminUserApi.class);
        doThrow(new ServiceException(123, "disabled")).when(users).validateUserList(List.of(9L));
        var policy = policy(definition(), users);
        var error = assertThrows(ServiceException.class, () -> policy.validate(7L));
        assertEquals(1_090_296_007, error.getCode());
        assertTrue(error.getMessage().contains("不存在或已停用"));
    }

    private PointsApprovalPolicy policy(BpmProcessDefinitionMetadataRespDTO d, AdminUserApi users) {
        var definitions = mock(BpmDefinitionReadApi.class);
        when(definitions.getPublishedProcessDefinition(PointsApprovalPolicy.PROCESS_KEY)).thenReturn(d);
        var user = new AdminUserRespDTO(); user.setDeptId(20L);
        when(users.getUser(7L)).thenReturn(user);
        var dept = new DeptRespDTO(); dept.setLeaderUserId(8L);
        var depts = mock(DeptApi.class); when(depts.getDept(20L)).thenReturn(dept);
        var policy = new PointsApprovalPolicy();
        ReflectionTestUtils.setField(policy, "definitions", definitions);
        ReflectionTestUtils.setField(policy, "users", users);
        ReflectionTestUtils.setField(policy, "depts", depts);
        return policy;
    }

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
