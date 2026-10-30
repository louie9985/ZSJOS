package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskPageReqVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BpmAllTodoMetricApiTest {
 @InjectMocks BpmProcessTaskApiImpl api;
 @Mock BpmTaskService tasks;
 @Mock BpmMetricQueryTransaction metricQueryTransaction;
 @Test void emptyMetricInstanceFilterNeverExpandsToAllWorkflowTasks(){
  var request=new cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO();request.setProcessDefinitionKey("appeal");request.setProcessInstanceIds(java.util.List.of());
  assertEquals(0L,api.getMetricTodoTaskPage(7L,request).getTotal());
  verify(metricQueryTransaction).check();verifyNoInteractions(tasks);
 }
 @Test void allDefinitionsDelegatesActorAndPaginationWithoutInventingDefinitionFilter(){
  when(tasks.getTaskTodoPage(eq(7L),any(BpmTaskPageReqVO.class),isNull(),isNull())).thenReturn(PageResult.empty(23L));
  var result=api.getAllTodoTaskPage(7L,3,5);
  assertEquals(23L,result.getTotal());
  var capture=ArgumentCaptor.forClass(BpmTaskPageReqVO.class);
  verify(tasks).getTaskTodoPage(eq(7L),capture.capture(),isNull(),isNull());
  assertEquals(3,capture.getValue().getPageNo());assertEquals(5,capture.getValue().getPageSize());
  assertNull(capture.getValue().getProcessDefinitionKey());
 }
 @Test void invalidActorOrPaginationDoesNotReachWorkflowEngine(){
  assertThrows(IllegalArgumentException.class,()->api.getAllTodoTaskPage(null,1,10));
  assertThrows(IllegalArgumentException.class,()->api.getAllTodoTaskPage(7L,0,10));
  assertThrows(IllegalArgumentException.class,()->api.getAllTodoTaskPage(7L,1,101));
  verifyNoInteractions(tasks);
 }
}
