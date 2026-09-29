package cn.iocoder.yudao.module.bpm.api.task;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class BpmBusinessReviewReasonTest {
 @InjectMocks BpmBusinessReviewApiImpl api;
 @Mock BpmTaskService taskService;
 @Mock BpmProcessInstanceService instanceService;
 @AfterEach void cleanup() { TenantContextHolder.clear(); }
 @Test void returnsSavedReasonRatherThanTaskDescription() { check("财务通过说明"); }
 @Test void emptyHistoricalReasonIsNotInvented() { check(null); }
 void check(String reason) {
  TenantContextHolder.setTenantId(9L);
  var process=mock(HistoricProcessInstance.class);
  when(instanceService.getHistoricProcessInstance("p1")).thenReturn(process);
  when(process.getTenantId()).thenReturn("9"); when(process.getProcessDefinitionKey()).thenReturn("withdrawal"); when(process.getBusinessKey()).thenReturn("withdrawal:1");
  when(process.getEndTime()).thenReturn(new Date());
  var task=mock(HistoricTaskInstance.class);
  when(task.getTaskDefinitionKey()).thenReturn("financeReview"); when(task.getEndTime()).thenReturn(new Date());
  var variables=new HashMap<String,Object>(); variables.put(BpmnVariableConstants.TASK_VARIABLE_STATUS,2); if(reason!=null) variables.put(BpmnVariableConstants.TASK_VARIABLE_REASON,reason);
  when(task.getTaskLocalVariables()).thenReturn(variables);
  when(taskService.getTaskListByProcessInstanceIds(Set.of("p1"))).thenReturn(List.of(task));
  assertEquals(reason,api.inspect("p1","withdrawal","withdrawal:1","financeReview").getReason());
  verify(task,never()).getDescription();
 }
}
