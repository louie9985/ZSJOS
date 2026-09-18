package cn.iocoder.yudao.module.zsjos.service.delivery;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class DeliveryPositioningSyncTest {
 @InjectMocks DeliveryPositioningSyncService service;
 @Mock MediaAccountMapper accounts; @Mock MediaAccountProfileEntryMapper entries; @Mock DeliveryPositioningSource sources;
 @Mock StudentDeliveryPlanMapper plans; @Mock StudentDeliveryPlanService planService;
 MediaAccountDO account;PositioningCardSubmissionDO source;StudentDeliveryPlanDO plan;
 @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);account=new MediaAccountDO().setId(10L).setVersion(1).setStudentPersonId(2L).setCreateServiceRelationId(3L);
 account.setCreateTime(LocalDateTime.of(2026,8,1,0,0));account.setDetailSnapshotJson("[]");
 source=new PositioningCardSubmissionDO().setId(30L).setSubmissionNo(2).setStatus("confirmed").setStudentDecidedAt(LocalDateTime.of(2026,9,1,10,0)).setSubmittedAt(LocalDateTime.of(2026,9,1,9,0));
 source.setEvidenceJson("[{\"id\":1,\"uploadedAt\":\"2026-09-01T12:00:00\"}]");source.setValuesSnapshotJson(JsonUtils.toJsonString(Map.of("pc_days7","7天要求","pc_days14","14天要求","pc_days28","28天要求","pc_student_duties","学员","pc_internal_goal","目标")));
 plan=new StudentDeliveryPlanDO().setId(1L).setAccountId(10L).setStatus("ACTIVE");
 when(accounts.selectByIdForUpdate(10L,1L)).thenReturn(account);when(sources.latest(account)).thenReturn(source);when(plans.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(plan);}
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void copiesSixFieldsClearsNullAndAuditsBefore(){account.setDetailValuesJson("{\"company_commitments\":\"旧公司约定\"}");when(sources.firstProven(account)).thenReturn(source);service.syncAccount(10L);
 var values=DeliveryPositioningSource.parse(account.getDetailValuesJson());assertEquals("7天要求",values.get("diagnosis_7d_requirement"));assertEquals("14天要求",values.get("diagnosis_14d_requirement"));assertEquals("28天要求",values.get("diagnosis_28d_requirement"));assertEquals("学员",values.get("student_commitments"));assertEquals("目标",values.get("delivery_goals"));assertFalse(values.containsKey("company_commitments"));verify(entries).insert(any(MediaAccountProfileEntryDO.class));}
 @Test void ordinaryVersionKeepsAnchor(){account.setDetailValuesJson(JsonUtils.toJsonString(Map.of("_diagnosisContext",JsonUtils.toJsonString(Map.of("anchorAt","2026-08-01T12:00:00","roundKey",5)))));service.syncAccount(10L);
 var ctx=cn.iocoder.yudao.module.zsjos.service.account.MediaAccountDiagnosisScheduler.context(account);assertEquals("2026-08-01T12:00:00",ctx.get("anchorAt"));assertEquals(5,ctx.get("roundKey"));verify(sources,never()).firstProven(any());}
 @Test void evidenceMissingDoesNotSynchronizeRequirements(){source.setEvidenceJson("[]");service.syncAccount(10L);verify(entries,never()).insert(any(MediaAccountProfileEntryDO.class));}
 @Test void replayPreservesManualCommitment(){when(entries.replay(10L,0L,"diagnosis-source-v1:10:30")).thenReturn(new MediaAccountProfileEntryDO());service.syncAccount(10L);verify(accounts,never()).updateById(any(MediaAccountDO.class));}
 @Test void restartUsesNewEffectiveSourceOnce(){
  plan.setStatus("REPOSITIONING").setSourceSubmissionId(5L).setRoundNo(1).setRestartRequestedAt(LocalDateTime.of(2026,8,30,0,0));
  account.setDetailValuesJson(JsonUtils.toJsonString(Map.of("_diagnosisContext",JsonUtils.toJsonString(Map.of("anchorAt","2026-08-01T12:00:00","roundKey",5)))));
  when(planService.ensurePlan(eq(2L),eq(10L),eq(3L),any(),eq(LocalDateTime.of(2026,9,1,12,0)))).thenReturn(new StudentDeliveryPlanDO());
  service.syncAccount(10L);
  var ctx=cn.iocoder.yudao.module.zsjos.service.account.MediaAccountDiagnosisScheduler.context(account);
  assertEquals("2026-09-01T12:00",ctx.get("anchorAt")); assertEquals(30,ctx.get("roundKey"));assertEquals("CLOSED",plan.getStatus());
 }
}
