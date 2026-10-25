package cn.iocoder.yudao.module.zsjos.service.account;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.extension.ExtendWith;import org.mockito.*;import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MediaAccountDiagnosisReminderTest {
 @InjectMocks MediaAccountDiagnosisReminderService service;
 @Mock BusinessTaskMapper tasks;@Mock MediaAccountMapper accounts;@Mock MediaAccountObjectPermissionProvider objects;
 @Mock BusinessEventMapper events;@Mock MediaWorkflowEventService eventService;@Mock StudentDeliveryPlanMapper plans;
 List<BusinessTaskDO> pending;
 void eligible(){var task=new BusinessTaskDO();task.setId(1L);task.setBizId(10L);task.setBizType("media_account_diagnosis");task.setDueAt(LocalDateTime.now().minusDays(1));task.setPayload("{\"roundKey\":2,\"cycle\":1,\"templateType\":\"diagnosis_7d\"}");pending=new ArrayList<>(List.of(task));
 when(tasks.selectMyPendingByBusiness(eq(20L),eq("media_account_diagnosis"),nullable(Long.class))).thenAnswer(call->{Long accountId=call.getArgument(2);return pending.stream().filter(t->accountId==null || accountId.equals(t.getBizId())).toList();});
 var account=new MediaAccountDO().setId(10L).setDirectorUserId(20L).setDetailValuesJson("{\"_diagnosisContext\":\"{\\\"roundKey\\\":2}\"}");lenient().when(accounts.selectById(10L)).thenReturn(account);lenient().when(objects.hasPermission(10L,"edit",20L)).thenReturn(true);}
 @Test void acknowledgeDoesNotCompleteTask(){eligible();service.acknowledge(20L,List.of(1L));verify(eventService).transition(eq("media_account_diagnosis"),eq(1L),eq(20L),isNull(),eq("REMINDER_SEEN"),isNull(),startsWith("diagnosis-seen:1:20:"));verify(tasks,never()).updateById(any(BusinessTaskDO.class));}
 @Test void dailySeenHidesPopupButKeepsPending(){eligible();when(events.selectByIdempotencyKey(anyString())).thenReturn(new BusinessEventDO());assertTrue(service.reminders(20L).isEmpty());assertEquals(1,service.pending(20L).size());}
 @Test void unauthorizedBatchWritesNothing(){eligible();assertThrows(RuntimeException.class,()->service.acknowledge(20L,List.of(1L,999L)));verifyNoInteractions(eventService);}
 @Test void otherUsersCannotAcknowledge(){when(tasks.selectMyPendingByBusiness(99L,"media_account_diagnosis",null)).thenReturn(List.of());assertThrows(RuntimeException.class,()->service.acknowledge(99L,List.of(1L)));verifyNoInteractions(eventService);}
 @Test void futureDeadlineIsFillableButDoesNotTriggerDuePopup(){eligible();pending.getFirst().setDueAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(7));assertEquals(1,service.accountTasks(10L,20L).size());assertTrue(service.reminders(20L).isEmpty());}
 @Test void oneDayBeforeDeadlineStartsReminder(){eligible();pending.getFirst().setDueAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1));assertEquals(1,service.reminders(20L).size());}
 @Test void moreThanOneDayBeforeDeadlineDoesNotRemind(){eligible();pending.getFirst().setDueAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1).plusMinutes(1));assertTrue(service.reminders(20L).isEmpty());}
 @Test void seenAdvanceReminderIsNotRepeatedToday(){eligible();pending.getFirst().setDueAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(12));when(events.selectByIdempotencyKey(anyString())).thenReturn(new BusinessEventDO());assertTrue(service.reminders(20L).isEmpty());assertEquals(1,service.accountTasks(10L,20L).size());}
 @Test void completedTasksAreNotReturnedForReminders(){when(tasks.selectMyPendingByBusiness(20L,"media_account_diagnosis",null)).thenReturn(List.of());assertTrue(service.reminders(20L).isEmpty());verifyNoInteractions(events);}
 @Test void overdueTaskRemainsFillable(){eligible();assertEquals(1,service.accountTasks(10L,20L).size());assertEquals(1,service.reminders(20L).size());}
 @Test void differentAccountHasNoTasks(){eligible();assertTrue(service.accountTasks(11L,20L).isEmpty());}
 @Test void oldRoundIsNotFillable(){eligible();accounts.selectById(10L).setDetailValuesJson("{\"_diagnosisContext\":\"{\\\"roundKey\\\":3}\"}");assertTrue(service.accountTasks(10L,20L).isEmpty());}
 @Test void editPermissionStillRequired(){eligible();when(objects.hasPermission(10L,"edit",20L)).thenReturn(false);assertTrue(service.accountTasks(10L,20L).isEmpty());}
 @Test void reassignedAccountIsNotFillableByOldDirector(){eligible();accounts.selectById(10L).setDirectorUserId(21L);lenient().when(objects.hasPermission(10L,"edit",20L)).thenReturn(true);assertTrue(service.accountTasks(10L,20L).isEmpty());}
 @Test void earliestDeadlineIsSelectedFirst(){eligible();var first=pending.getFirst();var later=new BusinessTaskDO();later.setId(2L);later.setBizId(10L);later.setBizType(first.getBizType());later.setPayload(first.getPayload());later.setDueAt(first.getDueAt().plusDays(7));pending=List.of(later,first);assertEquals(List.of(1L,2L),service.accountTasks(10L,20L).stream().map(t->t.taskId()).toList());}
 @Test void accountReadDoesNotLoadOtherAccountsAndChecksSharedAccountOnce(){
  eligible();var first=pending.getFirst();var second=new BusinessTaskDO();second.setId(2L);second.setBizId(10L);second.setBizType(first.getBizType());second.setPayload(first.getPayload());second.setDueAt(first.getDueAt());pending.add(second);
  var unrelated=new BusinessTaskDO();unrelated.setId(3L);unrelated.setBizId(11L);pending.add(unrelated);
  assertEquals(2,service.accountTasks(10L,20L).size());
  verify(tasks).selectMyPendingByBusiness(20L,"media_account_diagnosis",10L);verify(tasks,never()).selectMyPending(any());
  verify(tasks,never()).selectMyPendingByBusiness(20L,"media_account_diagnosis",null);
  verify(accounts,times(1)).selectById(10L);verify(accounts,never()).selectById(11L);
  verify(objects,times(1)).hasPermission(10L,"edit",20L);
 }
 @Test void revisionIntentKeepsCurrentRoundAvailable(){
  eligible();var plan=new cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryPlanDO();plan.setStatus("REPOSITIONING");
  lenient().when(plans.selectOne(any())).thenReturn(plan);assertEquals(1,service.accountTasks(10L,20L).size());
 }
 @Test void eligibilityIsFreshBetweenRequests(){
  eligible();assertEquals(1,service.accountTasks(10L,20L).size());when(objects.hasPermission(10L,"edit",20L)).thenReturn(false);
  assertTrue(service.accountTasks(10L,20L).isEmpty());verify(objects,times(2)).hasPermission(10L,"edit",20L);
 }
}
