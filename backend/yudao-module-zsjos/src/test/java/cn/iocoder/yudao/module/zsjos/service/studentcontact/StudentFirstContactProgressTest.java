package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.task.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants.*;

@ExtendWith(MockitoExtension.class)
class StudentFirstContactProgressTest {
    @InjectMocks StudentContactServiceImpl service;
    @Mock ServiceRelationMapper relationMapper;
    @Mock StudentContactRecordMapper recordMapper;
    @Mock StudentContactConfigVersionMapper configMapper;
    @Mock BusinessTaskMapper taskMapper;
    @Mock BusinessTaskCommandService taskCommandService;
    @Mock BusinessEventMapper eventMapper;
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi adminUserApi;
    @Mock StudentContactConfigService configService;
    ServiceRelationDO relation;
    StudentContactConfigVersionDO config;
    StudentFirstContactSubmitReqVO request;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        relation = new ServiceRelationDO(); relation.setId(10L); relation.setOwnerUserId(7L);
        relation.setAcceptanceStatus("accepted"); relation.setStatus("active"); relation.setVersion(2); relation.setDeliveryStage(STAGE_FIRST_CONTACT);
        config = new StudentContactConfigVersionDO(); config.setId(30L); config.setStudyPlanTimeoutMinutes(1440); config.setFirstContactTimeoutMinutes(120);
        config.setChecklistJson("[{\"key\":\"add\",\"title\":\"添加学员\",\"enabled\":true,\"sort\":1,\"attachmentRequired\":false},{\"key\":\"voice_learning_plan\",\"title\":\"语音沟通学习计划\",\"enabled\":true,\"sort\":2,\"attachmentRequired\":false}]");
        lenient().when(configService.requirePublished()).thenReturn(config);
        request = new StudentFirstContactSubmitReqVO(); request.setTaskId(20L); request.setSuccessful(true);
        request.setRemark("首联沟通"); request.setNextContactAt(LocalDateTime.now().plusHours(1)); request.setIdempotencyKey("first");
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    void prepareSubmit() {
        when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        BusinessTaskDO task = new BusinessTaskDO(); task.setId(20L); task.setTaskType(TYPE_FIRST_CONTACT); task.setStatus("pending");
        task.setBizType(BIZ_TYPE); task.setBizId(10L); task.setAssigneeId(7L); task.setIdempotencyKey("task"); task.setPayload("{\"configVersionId\":30}");
        when(taskMapper.selectByIdForUpdate(20L,1L)).thenReturn(task); lenient().when(configMapper.selectById(30L)).thenReturn(config);
        lenient().when(recordMapper.insert(any(StudentContactRecordDO.class))).thenAnswer(call -> { call.<StudentContactRecordDO>getArgument(0).setId(40L); return 1; });
        lenient().when(relationMapper.updateFirstContactProgress(eq(10L),eq(7L),eq(2),anyString())).thenReturn(1);
        lenient().when(relationMapper.advanceDeliveryStage(eq(10L),eq(7L),eq(STAGE_FIRST_CONTACT),anyString(),isNull(),eq(3))).thenReturn(1);
    }
    @Test void emptyChecklistStillCreatesPlan() {
        prepareSubmit(); service.submitFirstContact(10L,request,7L);
        var task=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class); verify(taskCommandService).create(task.capture());
        assertEquals(TYPE_STUDY_PLAN,task.getValue().taskType());
        verify(relationMapper).advanceDeliveryStage(10L,7L,STAGE_FIRST_CONTACT,STAGE_STUDY_PLAN,null,3);
    }
    @Test void selectedPlanCreatesOnlyContactAndKeepsSnapshot() {
        prepareSubmit(); request.setStudyPlanCompletedAtFirstContact(true); request.setCompletedChecklistKeys(List.of("add"));
        request.setNextContactAt(LocalDateTime.now().plusDays(10)); service.submitFirstContact(10L,request,7L);
        var task=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class); verify(taskCommandService,times(1)).create(task.capture());
        assertEquals(TYPE_CONTACT,task.getValue().taskType()); assertEquals(request.getNextContactAt(),task.getValue().dueAt());
        verify(relationMapper).advanceDeliveryStage(10L,7L,STAGE_FIRST_CONTACT,STAGE_SUPERVISION,null,3);
        var record=ArgumentCaptor.forClass(StudentContactRecordDO.class); verify(recordMapper).insert(record.capture());
        assertTrue(record.getValue().getStudyPlanCompletedAtFirstContact());
        var snapshot=JsonUtils.parseObject(record.getValue().getChecklistSnapshotJson(),StudentFirstContactProgressVO.class);
        assertEquals(1,snapshot.getItems().size()); assertEquals("添加学员",snapshot.getItems().getFirst().getTitle());
        assertTrue(snapshot.getItems().getFirst().getCompleted());
    }
    @Test void unknownAndRetiredKeysRejected() {
        prepareSubmit(); request.setCompletedChecklistKeys(List.of("voice_learning_plan"));
        assertThrows(RuntimeException.class,()->service.submitFirstContact(10L,request,7L)); verify(recordMapper,never()).insert(any(StudentContactRecordDO.class));
    }
    @Test void attachmentRequiredOnlyForSelectedItem() {
        config.setChecklistJson(config.getChecklistJson().replace("attachmentRequired\":false","attachmentRequired\":true"));
        assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(service,"validateChecklist",config,List.of(),List.of()));
        assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,"validateChecklist",config,List.of("add"),List.of()));
        assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(service,"validateChecklist",config,List.of("add"),List.of(1L)));
    }
    @Test void planChoiceParticipatesInReplayFingerprint() {
        prepareSubmit(); request.setStudyPlanCompletedAtFirstContact(true); service.submitFirstContact(10L,request,7L);
        var record=ArgumentCaptor.forClass(StudentContactRecordDO.class); verify(recordMapper).insert(record.capture());
        when(recordMapper.selectByIdempotencyKey("first")).thenReturn(record.getValue());
        assertEquals(40L,service.submitFirstContact(10L,request,7L)); request.setStudyPlanCompletedAtFirstContact(false);
        assertThrows(RuntimeException.class,()->service.submitFirstContact(10L,request,7L)); verify(taskCommandService,times(1)).create(any());
    }
    StudentFirstContactProgressVO progress() {
        return ReflectionTestUtils.invokeMethod(service,"firstContactSnapshot",config,List.of(),List.of());
    }
    void prepareUpdate() {
        lenient().when(permissionApi.hasAnyPermissions(7L,PERMISSION_CHECKLIST_UPDATE)).thenReturn(true);
        lenient().when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        StudentContactRecordDO first=new StudentContactRecordDO();first.setId(40L);first.setTaskId(20L);first.setOperatorUserId(7L);
        first.setChecklistSnapshotJson(JsonUtils.toJsonString(progress()));first.setSubmittedAt(LocalDateTime.now());first.setStudyPlanCompletedAtFirstContact(true);
        when(recordMapper.selectLatestSuccessful(10L,TYPE_FIRST_CONTACT)).thenReturn(first);
    }
    StudentFirstContactChecklistUpdateReqVO update() {
        var r=new StudentFirstContactChecklistUpdateReqVO();r.setVersion(2);r.setCompletedChecklistKeys(List.of("add"));r.setIdempotencyKey("update");return r;
    }
    @Test void updateChangesOnlyProgressAndAuditsReplay() {
        prepareUpdate();when(relationMapper.updateFirstContactProgress(eq(10L),eq(7L),eq(2),anyString())).thenReturn(1);
        var request=update();service.updateFirstContactChecklist(10L,request,7L);
        var event=ArgumentCaptor.forClass(BusinessEventDO.class);verify(eventMapper).insert(event.capture());
        assertTrue(event.getValue().getRelatedObjectRefs().contains("before"));assertTrue(event.getValue().getRelatedObjectRefs().contains("after"));
        verifyNoInteractions(taskCommandService);verify(recordMapper,never()).updateById(any(StudentContactRecordDO.class));
        when(eventMapper.selectByIdempotencyKey("student-checklist:10:update")).thenReturn(event.getValue());
        service.updateFirstContactChecklist(10L,request,7L);verify(eventMapper,times(1)).insert(any(BusinessEventDO.class));
        request.setCompletedChecklistKeys(List.of());assertThrows(RuntimeException.class,()->service.updateFirstContactChecklist(10L,request,7L));
    }
    @Test void deniedUpdateCannotReadOrMutate() {
        assertThrows(RuntimeException.class,()->service.updateFirstContactChecklist(10L,update(),7L));verifyNoInteractions(relationMapper,recordMapper,eventMapper,taskCommandService);
    }
    @Test void staleVersionCannotMutate() {
        when(permissionApi.hasAnyPermissions(7L,PERMISSION_CHECKLIST_UPDATE)).thenReturn(true);when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        var r=update();r.setVersion(1);assertThrows(RuntimeException.class,()->service.updateFirstContactChecklist(10L,r,7L));
        verify(recordMapper,never()).selectLatestSuccessful(anyLong(),anyString());verify(eventMapper,never()).insert(any(BusinessEventDO.class));
    }
    @Test void missingHistoricConfigDoesNotInventCompletion() {
        StudentContactRecordDO first=new StudentContactRecordDO();first.setId(40L);first.setTaskId(20L);
        var snapshot=ReflectionTestUtils.<StudentFirstContactProgressVO>invokeMethod(service,"recordSnapshot",first);
        assertTrue(snapshot.getHistoryIncomplete());assertNull(snapshot.getStudyPlanCompletedAtFirstContact());assertTrue(snapshot.getItems().isEmpty());
    }
    @Test void failedContactCannotClaimPlanOrChecklist() {
        prepareSubmit(); request.setSuccessful(false); request.setStudyPlanCompletedAtFirstContact(true);
        assertThrows(RuntimeException.class,()->service.submitFirstContact(10L,request,7L));
        request.setStudyPlanCompletedAtFirstContact(false);request.setCompletedChecklistKeys(List.of("add"));
        assertThrows(RuntimeException.class,()->service.submitFirstContact(10L,request,7L));
        verifyNoInteractions(taskCommandService);verify(recordMapper,never()).insert(any(StudentContactRecordDO.class));
    }
    @Test void snapshotDoesNotFollowConfigurationRename() {
        var snapshot=progress();config.setChecklistJson(config.getChecklistJson().replace("添加学员","新名称"));
        StudentContactRecordDO record=new StudentContactRecordDO();record.setChecklistSnapshotJson(JsonUtils.toJsonString(snapshot));
        var restored=ReflectionTestUtils.<StudentFirstContactProgressVO>invokeMethod(service,"recordSnapshot",record);
        assertEquals("添加学员",restored.getItems().getFirst().getTitle());verifyNoInteractions(configMapper,taskMapper);
    }
    @Test void historicalTaskRestoresOriginalRetiredItemWithoutInventingPlanFlag() {
        StudentContactRecordDO record=new StudentContactRecordDO();record.setTaskId(20L);record.setChecklistResultJson("[\"voice_learning_plan\"]");
        BusinessTaskDO task=new BusinessTaskDO();task.setPayload("{\"configVersionId\":30}");
        when(taskMapper.selectById(20L)).thenReturn(task);lenient().when(configMapper.selectById(30L)).thenReturn(config);
        var result=ReflectionTestUtils.<StudentFirstContactProgressVO>invokeMethod(service,"recordSnapshot",record);
        assertFalse(result.getHistoryIncomplete());assertNull(result.getStudyPlanCompletedAtFirstContact());
        assertTrue(result.getItems().stream().anyMatch(item->RETIRED_PLAN_CHECKLIST_KEY.equals(item.getKey()) && item.getCompleted()));
    }
    @Test void pausedAndTransferredFormerOwnerCannotUpdate() {
        when(permissionApi.hasAnyPermissions(7L,PERMISSION_CHECKLIST_UPDATE)).thenReturn(true);
        when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        relation.setStatus("paused");assertThrows(RuntimeException.class,()->service.updateFirstContactChecklist(10L,update(),7L));
        relation.setStatus("active");relation.setOwnerUserId(8L);assertThrows(RuntimeException.class,()->service.updateFirstContactChecklist(10L,update(),7L));
        verifyNoInteractions(recordMapper,eventMapper,taskCommandService);
    }
    @Test void currentOwnerCanCorrectPreviousCompletion() {
        prepareUpdate();var stored=progress();stored.getItems().getFirst().setCompleted(true);
        relation.setFirstContactProgressJson(JsonUtils.toJsonString(stored));
        when(relationMapper.updateFirstContactProgress(eq(10L),eq(7L),eq(2),anyString())).thenReturn(1);
        var r=update();r.setCompletedChecklistKeys(List.of());service.updateFirstContactChecklist(10L,r,7L);
        var json=ArgumentCaptor.forClass(String.class);verify(relationMapper).updateFirstContactProgress(eq(10L),eq(7L),eq(2),json.capture());
        assertFalse(JsonUtils.parseObject(json.getValue(),StudentFirstContactProgressVO.class).getItems().getFirst().getCompleted());
        verifyNoInteractions(taskCommandService);
    }
    @Test void optimisticConflictDoesNotWriteAudit() {
        prepareUpdate();assertThrows(RuntimeException.class,()->service.updateFirstContactChecklist(10L,update(),7L));
        verify(eventMapper,never()).insert(any(BusinessEventDO.class));verifyNoInteractions(taskCommandService);
    }
    @Test void summaryQueriesExactServiceWithoutHistoryPagination() {
        prepareUpdate();clearInvocations(permissionApi,relationMapper);var result=ReflectionTestUtils.<StudentFirstContactProgressVO>invokeMethod(service,"firstContactProgress",relation);
        assertEquals("first_contact",result.getStudyPlanStatus());verify(recordMapper).selectLatestSuccessful(10L,TYPE_FIRST_CONTACT);
        verify(recordMapper,never()).selectPageByRelationId(any(),anyLong());
    }

    @Test void completedPlanMayLeaveNextContactEmpty() {
        prepareSubmit();request.setStudyPlanCompletedAtFirstContact(true);request.setNextContactAt(null);
        service.submitFirstContact(10L,request,7L);verify(taskCommandService,never()).create(any());
        verify(taskCommandService).completeByKey(eq("task"),any());
        verify(relationMapper).advanceDeliveryStage(10L,7L,STAGE_FIRST_CONTACT,STAGE_SUPERVISION,null,3);
    }
    @Test void unfinishedPlanStillRequiresNextTime() {
        prepareSubmit();request.setNextContactAt(null);
        assertThrows(RuntimeException.class,()->service.submitFirstContact(10L,request,7L));verifyNoInteractions(taskCommandService);
    }
    @Test void oldFirstTaskCreatesPlanWithNewPublishedPolicy() {
        prepareSubmit();var published=new StudentContactConfigVersionDO();published.setId(99L);published.setStudyPlanTimeoutMinutes(4320);
        when(configService.requirePublished()).thenReturn(published);request.setNextContactAt(LocalDateTime.now().plusDays(3));
        service.submitFirstContact(10L,request,7L);var task=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);
        verify(taskCommandService).create(task.capture());assertEquals(request.getNextContactAt(),task.getValue().dueAt());
        assertTrue(task.getValue().payload().contains("99"));assertEquals(1440,config.getStudyPlanTimeoutMinutes());
    }
    StudentContactSubmitReqVO ordinaryRequest() {
        var r=new StudentContactSubmitReqVO();r.setSuccessful(true);r.setRemark("主动跟进");r.setIdempotencyKey("ordinary");return r;
    }
    @Test void ordinaryContactWithoutTaskOrTimePersistsAndReplays() {
        relation.setDeliveryStage(STAGE_SUPERVISION);when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        var r=ordinaryRequest();service.submitContact(10L,r,7L);
        var saved=ArgumentCaptor.forClass(StudentContactRecordDO.class);verify(recordMapper).insert(saved.capture());
        assertNull(saved.getValue().getTaskId());assertNull(saved.getValue().getNextContactAt());verifyNoInteractions(taskCommandService);
        when(recordMapper.selectByIdempotencyKey("ordinary")).thenReturn(saved.getValue());service.submitContact(10L,r,7L);
        verify(recordMapper,times(1)).insert(any(StudentContactRecordDO.class));
    }
    @Test void tasklessContactFindsAndCompletesExistingOrdinaryTask() {
        relation.setDeliveryStage(STAGE_SUPERVISION);when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        var task=new BusinessTaskDO();task.setId(20L);task.setTaskType(TYPE_CONTACT);task.setBizType(BIZ_TYPE);task.setBizId(10L);
        task.setAssigneeId(7L);task.setStatus("pending");task.setPayload("{\"configVersionId\":30}");task.setIdempotencyKey("ordinary-task");
        when(taskMapper.selectPendingByRelationAndType(eq(10L),anyString())).thenAnswer(call -> TYPE_CONTACT.equals(call.getArgument(1)) ? task : null);
        when(taskMapper.selectByIdForUpdate(20L,1L)).thenReturn(task);lenient().when(configMapper.selectById(30L)).thenReturn(config);
        service.submitContact(10L,ordinaryRequest(),7L);verify(taskCommandService).completeByKey(eq("ordinary-task"),any());verify(taskCommandService,never()).create(any());
    }
    @Test void tasklessFollowUpCannotBypassPlanStage() {
        relation.setDeliveryStage(STAGE_STUDY_PLAN);when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        assertThrows(RuntimeException.class,()->service.submitContact(10L,ordinaryRequest(),7L));verifyNoInteractions(taskCommandService);
    }
    @Test void scheduledOrdinaryContactCreatesOneReminder() {
        relation.setDeliveryStage(STAGE_SUPERVISION);when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        var r=ordinaryRequest();r.setNextContactAt(LocalDateTime.now().plusDays(20));service.submitContact(10L,r,7L);
        var task=ArgumentCaptor.forClass(BusinessTaskCreateCommand.class);verify(taskCommandService).create(task.capture());
        assertEquals(TYPE_CONTACT,task.getValue().taskType());assertEquals(r.getNextContactAt(),task.getValue().dueAt());
    }

    @Test void studyPlanSuccessClosesTaskWithoutSchedulingAnother() {
        relation.setDeliveryStage(STAGE_STUDY_PLAN);when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        var task=new BusinessTaskDO();task.setId(20L);task.setTaskType(TYPE_STUDY_PLAN);task.setBizType(BIZ_TYPE);task.setBizId(10L);
        task.setAssigneeId(7L);task.setStatus("pending");task.setPayload("{\"configVersionId\":30}");task.setIdempotencyKey("plan-task");
        when(taskMapper.selectByIdForUpdate(20L,1L)).thenReturn(task);when(configMapper.selectById(30L)).thenReturn(config);
        when(relationMapper.advanceDeliveryStage(10L,7L,STAGE_STUDY_PLAN,STAGE_SUPERVISION,null,2)).thenReturn(1);
        var r=new StudentStudyPlanSubmitReqVO();r.setTaskId(20L);r.setSuccessful(true);r.setRemark("计划完成");r.setIdempotencyKey("plan-done");
        service.submitStudyPlan(10L,r,7L);verify(taskCommandService).completeByKey(eq("plan-task"),any());verify(taskCommandService,never()).create(any());
    }
    @Test void unfinishedStudyPlanCannotOmitNextTime() {
        relation.setDeliveryStage(STAGE_STUDY_PLAN);when(relationMapper.selectByIdForUpdate(10L,1L)).thenReturn(relation);
        var task=new BusinessTaskDO();task.setId(20L);task.setTaskType(TYPE_STUDY_PLAN);task.setBizType(BIZ_TYPE);task.setBizId(10L);
        task.setAssigneeId(7L);task.setStatus("pending");when(taskMapper.selectByIdForUpdate(20L,1L)).thenReturn(task);
        var r=new StudentStudyPlanSubmitReqVO();r.setTaskId(20L);r.setSuccessful(false);r.setRemark("尚未完成");r.setIdempotencyKey("plan-later");
        assertThrows(RuntimeException.class,()->service.submitStudyPlan(10L,r,7L));verifyNoInteractions(taskCommandService);
    }

}
