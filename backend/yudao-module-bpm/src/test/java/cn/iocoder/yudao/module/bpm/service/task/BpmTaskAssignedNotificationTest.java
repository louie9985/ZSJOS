package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;
import static org.mockito.Mockito.*;

class BpmTaskAssignedNotificationTest {
    @Test void commitsDeliverForPartnerMissingAndUnavailableAdminButRollbackDoesNot() {
        for (String subject : new String[] {"3:20", "20", "unavailable"}) {
            BpmTaskServiceImpl service = new BpmTaskServiceImpl();
            BpmProcessInstanceService instances = mock(BpmProcessInstanceService.class);
            BpmProcessDefinitionService definitions = mock(BpmProcessDefinitionService.class);
            BpmModelService models = mock(BpmModelService.class);
            BpmMessageService messages = mock(BpmMessageService.class);
            AdminUserApi users = mock(AdminUserApi.class);
            ReflectionTestUtils.setField(service, "processInstanceService", instances);
            ReflectionTestUtils.setField(service, "bpmProcessDefinitionService", definitions);
            ReflectionTestUtils.setField(service, "modelService", models);
            ReflectionTestUtils.setField(service, "runtimeService", mock(RuntimeService.class));
            ReflectionTestUtils.setField(service, "messageService", messages);
            ReflectionTestUtils.setField(service, "adminUserApi", users);
            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getStartUserId()).thenReturn(subject.equals("unavailable") ? "20" : subject);
            when(instance.getTenantId()).thenReturn("1"); when(instance.getProcessDefinitionId()).thenReturn("definition");
            when(instance.getProcessInstanceId()).thenReturn("process");
            when(instance.getProcessVariables()).thenReturn(Map.of("externalStartUserName", "历史合作方"));
            when(instances.getProcessInstance("process")).thenReturn(instance);
            when(definitions.getProcessDefinitionInfo("definition")).thenReturn(new BpmProcessDefinitionInfoDO());
            BpmnModel model = new BpmnModel(); var process = new org.flowable.bpmn.model.Process(); process.setId("flow");
            var node = new UserTask(); node.setId("review"); process.addFlowElement(node); model.addProcess(process);
            when(models.getBpmnModelByDefinitionId("definition")).thenReturn(model);
            if (subject.equals("unavailable")) when(users.getUser(20L)).thenThrow(new IllegalStateException("unavailable"));
            Task task = mock(Task.class); when(task.getId()).thenReturn("task"); when(task.getAssignee()).thenReturn("10");
            when(task.getProcessDefinitionId()).thenReturn("definition"); when(task.getProcessInstanceId()).thenReturn("process");
            when(task.getTaskDefinitionKey()).thenReturn("review");
            TransactionSynchronizationManager.initSynchronization();
            try {
                service.processTaskAssigned(task);
                var callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                verifyNoInteractions(messages);
                callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                verify(messages).sendMessageWhenTaskAssigned(argThat(dto -> dto.getAssigneeUserId().equals(10L)
                        && dto.getStartUserNickname().equals(subject.equals("3:20") ? "历史合作方" : "发起人信息不可用")));
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
    }
}
