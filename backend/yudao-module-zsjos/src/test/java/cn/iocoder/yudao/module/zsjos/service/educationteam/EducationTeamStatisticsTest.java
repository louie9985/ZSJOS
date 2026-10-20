package cn.iocoder.yudao.module.zsjos.service.educationteam;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class EducationTeamStatisticsTest {
 BusinessTaskDO task(long id,long lead,String type,String status,LocalDateTime due){var t=new BusinessTaskDO();t.setId(id);t.setBizId(lead);t.setBizType("lead");t.setTaskType(type);t.setStatus(status);t.setDueAt(due);return t;}
 @Test void statisticsDeduplicateLeadsExcludeAutomaticTasksAndPreserveEffectiveTime() {
  var service=new EducationTeamServiceImpl();var user=new AdminUserRespDTO();user.setId(2L);user.setNickname("教务");user.setAvatar("fixture-avatar");user.setStatus(0);
  var start=LocalDate.of(2026,10,18).atStartOfDay();var tasks=new ArrayList<BusinessTaskDO>();
  tasks.add(task(1,5,"lead_first_follow_up","pending",start.plusHours(9)));tasks.add(task(2,5,"lead_follow_up_reminder","completed",start.plusHours(9)));tasks.add(task(3,6,"lead_follow_up_reminder","completed",start.plusHours(10)));tasks.add(task(4,7,"lead_follow_up_reminder","cancelled",start.plusHours(9)));
  var automatic=task(5,8,"lead_first_follow_up","pending",start.plusHours(9));automatic.setPayload("{\"generationSource\":\"education_self_sourced_auto\"}");tasks.add(automatic);
  var order=new SalesOrderDO();order.setLeadId(5L);order.setTotalAmount(new BigDecimal("1280"));order.setEffectiveAt(start.plusHours(10));
  var older=new SalesOrderDO();older.setLeadId(5L);older.setTotalAmount(new BigDecimal("200"));older.setEffectiveAt(start.minusSeconds(1));
  var pending=new LeadDO();pending.setStatus("submitted");var valid=new LeadDO();valid.setStatus("valid");
  EducationTeamRespVO row=ReflectionTestUtils.invokeMethod(service,"buildRow",user,List.of(pending,valid),tasks,List.of(order,older),List.of(),false,start,start.plusDays(1),start.plusHours(12));
  assertNotNull(row);assertEquals(2,row.getTodayFollowUpTotalCount());assertEquals(1,row.getTodayFollowUpRemainingCount());assertEquals(1,row.getTodayPendingCount());assertEquals(1,row.getPendingQualificationCount());assertEquals(new BigDecimal("1280"),row.getTodayOrderAmount());assertEquals(new BigDecimal("1480"),row.getEffectiveOrderAmount());assertEquals(1,row.getConvertedLeadCount());assertEquals("fixture-avatar",row.getAvatar());
 }
 @Test void tasksOnlyExposeCurrentEducationOwnershipAndEmptySetsNeverQueryLeads() {
  var service=new EducationTeamServiceImpl();var scope=mock(EducationTeamScope.class);var tasks=mock(BusinessTaskMapper.class);var leads=mock(LeadMapper.class);
  ReflectionTestUtils.setField(service,"scope",scope);ReflectionTestUtils.setField(service,"taskMapper",tasks);ReflectionTestUtils.setField(service,"leadMapper",leads);
  var user=new AdminUserRespDTO();user.setId(2L);when(scope.readable(1L)).thenReturn(List.of(user));
  assertEquals(0,service.getTaskPage(2L,new EducationTeamTaskPageReqVO(),1L).getTotal());verifyNoInteractions(leads);
  var education=new LeadDO();education.setId(5L);education.setOwnerIdentity("education");education.setOwnerUserId(2L);education.setLeadNo("KZ-005");
  var sales=new LeadDO();sales.setId(6L);sales.setOwnerIdentity("sales");sales.setOwnerUserId(2L);
  when(tasks.selectMyPending(2L)).thenReturn(List.of(task(1,5,"lead_follow_up_reminder","pending",null),task(2,6,"lead_follow_up_reminder","pending",null)));when(leads.selectBatchIds(Set.of(5L,6L))).thenReturn(List.of(education,sales));
  var page=service.getTaskPage(2L,new EducationTeamTaskPageReqVO(),1L);assertEquals(1,page.getTotal());assertEquals("KZ-005",page.getList().getFirst().getLeadNo());
 }
}
