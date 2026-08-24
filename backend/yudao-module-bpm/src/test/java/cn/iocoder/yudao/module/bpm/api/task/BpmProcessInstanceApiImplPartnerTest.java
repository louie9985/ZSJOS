package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

class BpmProcessInstanceApiImplPartnerTest {

    @Test
    void typedBusinessTerminationRejectsBlankAuditParameters() {
        BpmProcessInstanceApiImpl api = new BpmProcessInstanceApiImpl();
        BpmProcessInstanceService service = mock(BpmProcessInstanceService.class);
        ReflectionTestUtils.setField(api, "processInstanceService", service);

        assertThrows(IllegalArgumentException.class, () -> api.terminateProcessInstanceByBusiness(
                new BpmStartSubjectDTO(UserTypeEnum.PARTNER.getValue(), 20L), "process-1", " ", "reason"));
        verifyNoInteractions(service);
    }

    @Test
    void externalSubjectErrorsHaveUniqueCodes() {
        assertNotEquals(PROCESS_INSTANCE_HTTP_CALL_ERROR.getCode(), PROCESS_INSTANCE_EXTERNAL_USER_INVALID.getCode());
        assertNotEquals(PROCESS_INSTANCE_APPROVE_USER_SELECT_ASSIGNEES_NOT_CONFIG.getCode(),
                PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode());
        assertNotEquals(PROCESS_INSTANCE_EXTERNAL_USER_INVALID.getCode(),
                PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode());
    }

    @Test
    void processInstanceErrorCodesAreUnique() throws IllegalAccessException {
        List<Integer> codes = Arrays.stream(cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.class.getFields())
                .filter(field -> field.getName().startsWith("PROCESS_INSTANCE_"))
                .map(this::errorCode).toList();

        assertEquals(codes.size(), codes.stream().distinct().count());
    }

    @Test
    void externalSubjectAllowsConfiguredStartUserSelectWithEnabledInternalReviewer() {
        BpmProcessInstanceServiceImpl service = new BpmProcessInstanceServiceImpl();
        BpmModelService modelService = mock(BpmModelService.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        ReflectionTestUtils.setField(service, "modelService", modelService);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        UserTask task = task("financeReview", "财务审批", 35);
        Process process = new Process();
        process.addFlowElement(task);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(modelService.getBpmnModelByDefinitionId("definition-1")).thenReturn(model);
        when(adminUserApi.getUserMap(List.of(30L))).thenReturn(Map.of(30L,
                new AdminUserRespDTO().setId(30L).setStatus(0)));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service,
                "validateExternalCandidateStrategies", "definition-1",
                Map.of("financeReview", List.of(30L))));
    }

    @Test
    void externalSubjectStillRejectsOrganizationDependentStarterStrategy() {
        BpmProcessInstanceServiceImpl service = new BpmProcessInstanceServiceImpl();
        BpmModelService modelService = mock(BpmModelService.class);
        ReflectionTestUtils.setField(service, "modelService", modelService);
        Process process = new Process();
        process.addFlowElement(task("selfReview", "本人确认", 36));
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(modelService.getBpmnModelByDefinitionId("definition-1")).thenReturn(model);

        assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "validateExternalCandidateStrategies", "definition-1", null));
    }

    private UserTask task(String id, String name, int strategy) {
        UserTask task = new UserTask();
        task.setId(id);
        task.setName(name);
        BpmnModelUtils.addCandidateElements(strategy, null, task);
        return task;
    }

    private Integer errorCode(Field field) {
        try {
            return ((cn.iocoder.yudao.framework.common.exception.ErrorCode) field.get(null)).getCode();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
