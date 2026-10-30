package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.enums.LeadConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadAppealMetricPageTest {
 @InjectMocks LeadAppealServiceImpl service;
 @Mock PermissionApi permissionApi; @Mock AdminUserApi adminUserApi;
 @Mock BpmProcessTaskApi processTaskApi; @Mock AdvancedFilterService advancedFilterService;
 @Mock LeadAppealMapper appealMapper;
 void reviewer(){
  when(permissionApi.hasAnyPermissions(eq(7L),anyString())).thenAnswer(call->List.of("zsjos:lead:appeal:query",LeadConstants.PERMISSION_APPEAL_REVIEW_SALES_MANAGER).contains(call.getArgument(1)));
  var user=new AdminUserRespDTO();user.setId(7L);user.setStatus(0);when(adminUserApi.getUser(7L)).thenReturn(user);
 }
 @Test void filteredMetricUsesOneAuthorizedBpmPageWithoutScanningAllTasks(){
  reviewer();var filter=new AdvancedFilterGroupReqVO();var request=new LeadAppealPageReqVO();request.setPageNo(5);request.setPageSize(10);request.setAdvancedFilter(filter);
  when(advancedFilterService.matchAppealIds(isNull(),same(filter))).thenReturn(List.of(12L,13L));
  when(appealMapper.selectProcessInstanceIdsByAppealIds(List.of(12L,13L))).thenReturn(List.of("process12","process13"));
  when(processTaskApi.getMetricTodoTaskPage(eq(7L),any())).thenReturn(PageResult.empty(120L));
  assertEquals(120L,service.getMetricInboxPage(request,7L).getTotal());
  var capture=ArgumentCaptor.forClass(BpmTaskPageReqDTO.class);verify(processTaskApi,times(1)).getMetricTodoTaskPage(eq(7L),capture.capture());
  assertEquals(5,capture.getValue().getPageNo());assertEquals(10,capture.getValue().getPageSize());
  assertEquals(List.of("process12","process13"),capture.getValue().getProcessInstanceIds());
  assertEquals(LeadConstants.APPEAL_PROCESS_DEFINITION_KEY,capture.getValue().getProcessDefinitionKey());
  assertEquals(LeadConstants.APPEAL_TASK_DEFINITION_KEY,capture.getValue().getTaskDefinitionKey());
  assertEquals("reviewStage",capture.getValue().getProcessVariableName());
  verify(processTaskApi,never()).getTodoTaskPage(anyLong(),any());
 }
 @Test void emptyBusinessFilterRemainsExplicitlyEmptyAtBpmBoundary(){
  reviewer();var filter=new AdvancedFilterGroupReqVO();var request=new LeadAppealPageReqVO();request.setAdvancedFilter(filter);
  when(advancedFilterService.matchAppealIds(isNull(),same(filter))).thenReturn(List.of());
  when(appealMapper.selectProcessInstanceIdsByAppealIds(List.of())).thenReturn(List.of());
  when(processTaskApi.getMetricTodoTaskPage(eq(7L),any())).thenReturn(PageResult.empty());
  assertEquals(0L,service.getMetricInboxPage(request,7L).getTotal());
  var capture=ArgumentCaptor.forClass(BpmTaskPageReqDTO.class);verify(processTaskApi).getMetricTodoTaskPage(eq(7L),capture.capture());
  assertNotNull(capture.getValue().getProcessInstanceIds());assertTrue(capture.getValue().getProcessInstanceIds().isEmpty());
 }
}
