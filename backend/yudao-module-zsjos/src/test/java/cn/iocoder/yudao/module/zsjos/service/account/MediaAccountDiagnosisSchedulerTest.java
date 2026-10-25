package cn.iocoder.yudao.module.zsjos.service.account;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryPlanMapper;
import cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSyncService;
import cn.iocoder.yudao.module.zsjos.service.task.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class MediaAccountDiagnosisSchedulerTest {
 @InjectMocks MediaAccountDiagnosisScheduler scheduler;
 @Mock MediaAccountMapper accounts; @Mock BusinessTaskMapper taskMapper; @Mock StudentDeliveryPlanMapper plans;
 @Mock DeliveryPositioningSyncService sync; @Mock BusinessTaskCommandService tasks;
 MediaAccountDO account;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);account=new MediaAccountDO().setId(10L).setDirectorUserId(20L);
   when(accounts.selectActiveForDiagnosis(1L)).thenReturn(List.of(account));when(accounts.selectById(10L)).thenReturn(account);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 void context(String anchor,long source){account.setDetailValuesJson(JsonUtils.toJsonString(Map.of("_diagnosisContext",JsonUtils.toJsonString(Map.of("anchorAt",anchor,"roundKey",source,"submissionId",99)),"diagnosis_14d_requirement","验证指标")));}
 @Test void missingProvenAnchorDoesNotInventCreationDay(){scheduler.generateTenant(LocalDate.of(2026,9,29));verify(tasks,never()).create(any());}
 @Test void day28CreatesSeparateCyclesAndOriginalDueDates(){context("2026-09-01T12:00:00",1);scheduler.generateTenant(LocalDate.of(2026,9,29));
  var cap=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);verify(tasks,times(10)).create(cap.capture());
  assertEquals(10,cap.getAllValues().stream().map(BusinessTaskCreateCommand::idempotencyKey).distinct().count());
  assertTrue(cap.getAllValues().stream().anyMatch(c->c.dueAt().equals(LocalDateTime.of(2026,9,8,0,0))));
  assertTrue(cap.getAllValues().stream().anyMatch(c->c.payload().contains("验证指标")));}
 @Test void currentPeriodsOpenBeforeDeadline(){context("2026-09-01T12:00:00",1);scheduler.generateTenant(LocalDate.of(2026,9,1));
  var cap=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);verify(tasks,times(3)).create(cap.capture());
  assertEquals(List.of(LocalDateTime.of(2026,9,8,0,0),LocalDateTime.of(2026,9,15,0,0),LocalDateTime.of(2026,9,29,0,0)),cap.getAllValues().stream().map(BusinessTaskCreateCommand::dueAt).toList());}
 @Test void futureAnchorDoesNotOpenTasks(){context("2026-09-02T12:00:00",1);scheduler.generateTenant(LocalDate.of(2026,9,1));verify(tasks,never()).create(any());}
 @Test void deadlineBoundaryOpensNextPeriodWithoutMovingOldDeadline(){context("2026-09-01T12:00:00",1);scheduler.generateTenant(LocalDate.of(2026,9,8));
  var cap=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);verify(tasks,times(4)).create(cap.capture());
  var seven=cap.getAllValues().stream().filter(c->c.taskType().equals("media_account_diagnosis_7d")).toList();
  assertEquals(List.of(LocalDateTime.of(2026,9,8,0,0),LocalDateTime.of(2026,9,15,0,0)),seven.stream().map(BusinessTaskCreateCommand::dueAt).toList());}
 @Test void repeatedSchedulingUsesSameKey(){context("2026-09-01T12:00:00",1);scheduler.generateTenant(LocalDate.of(2026,9,8));scheduler.generateTenant(LocalDate.of(2026,9,8));
  verify(tasks,times(2)).create(argThat(c->c.idempotencyKey().equals("media-diagnosis-v2:10:1:media_account_diagnosis_7d:1")));}
 @Test void restartedRoundCannotReuseOldTaskKey(){context("2026-09-01T12:00:00",2);scheduler.generateTenant(LocalDate.of(2026,9,8));assertNotEquals(MediaAccountDiagnosisScheduler.key(10L,1,"type",1),MediaAccountDiagnosisScheduler.key(10L,2,"type",1));}
 @Test void revisionIntentDoesNotCloseCurrentDiagnosisRound(){
  context("2026-09-01T12:00:00",1);
  scheduler.generateTenant(LocalDate.of(2026,9,1));
  verify(tasks,times(3)).create(any());verifyNoInteractions(plans);
 }
 @Test void newRoundCancelsOnlyOldPendingTasksAndKeepsCurrentRound(){
  com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO.class);
  context("2026-09-01T12:00:00",2);
  var old=new cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO();old.setId(1L);old.setPayload("{\"roundKey\":1}");
  var current=new cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO();current.setId(2L);current.setPayload("{\"roundKey\":2}");
  when(taskMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(old,current));
  scheduler.generateTenant(LocalDate.of(2026,9,1));
  var updates=ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
  verify(taskMapper,times(1)).update(isNull(),updates.capture());
  assertTrue(updates.getValue().getSqlSegment().contains("status"));
  assertTrue(updates.getValue().getParamNameValuePairs().containsValue("pending"));
  assertTrue(updates.getValue().getParamNameValuePairs().containsValue(1L));
  verify(tasks,times(3)).create(any());
 }
}
