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
        ReflectionTestUtils.setField(service, "processDefinitionService",
                mock(cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService.class));
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
        ReflectionTestUtils.setField(service, "processDefinitionService",
                mock(cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService.class));
        BpmModelService modelService = mock(BpmModelService.class);
        ReflectionTestUtils.setField(service, "modelService", modelService);
        Process process = new Process();
        process.addFlowElement(task("selfReview", "本人确认", 36));
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(modelService.getBpmnModelByDefinitionId("definition-1")).thenReturn(model);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service,
                        "validateExternalCandidateStrategies", "definition-1", null));
        assertEquals(PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode(), error.getCode());
    }

    @Test
    void migratedSimpleAssetsAllowExternalStarterOnlyForGeneratedSubmission() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
        while (root != null && !java.nio.file.Files.isRegularFile(root.resolve("script/bpm/manifest.json"))) {
            root = root.getParent();
        }
        assertNotNull(root);
        for (String key : List.of("zsjos_partner_withdrawal", "zsjos_lead_appeal_review")) {
            var req = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                    java.nio.file.Files.readString(root.resolve("script/bpm/" + key + "/2.0.0/process-model.json")),
                    cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO.class);
            BpmnModel model = cn.iocoder.yudao.module.bpm.framework.flowable.core.util.SimpleModelUtils
                    .buildBpmnModel(req.getKey(), req.getName(), req.getSimpleModel());
            BpmProcessInstanceServiceImpl service = new BpmProcessInstanceServiceImpl();
            BpmModelService modelService = mock(BpmModelService.class);
            ReflectionTestUtils.setField(service, "modelService", modelService);
            when(modelService.getBpmnModelByDefinitionId("definition-1")).thenReturn(model);
            var definitions = mock(cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService.class);
            var info = new cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO()
                    .setModelType(20).setSimpleModel(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                            .toJsonString(req.getSimpleModel()));
            ReflectionTestUtils.setField(service, "processDefinitionService", definitions);
            when(definitions.getProcessDefinitionInfo("definition-1")).thenReturn(info);
            var users = mock(AdminUserApi.class);
            ReflectionTestUtils.setField(service, "adminUserApi", users);
            when(users.getUserMap(List.of(30L))).thenReturn(Map.of(30L,
                    new AdminUserRespDTO().setId(30L).setStatus(0)));
            String taskKey = key.contains("withdrawal") ? "financeReview" : "appealReview";
            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "validateExternalCandidateStrategies",
                    "definition-1", Map.of(taskKey, List.of(30L))));
            info.setModelType(10);
            var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> ReflectionTestUtils.invokeMethod(service, "validateExternalCandidateStrategies",
                            "definition-1", Map.of(taskKey, List.of(30L))));
            assertEquals(PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode(), error.getCode());
        }
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
