package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigRespVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class MediaAccountProfileServiceTest {
    @org.mockito.Mock private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
    @Test void retiredPositioningCannotBeEditedThroughProfileEvenIfConfiguredEnabled() {
        var legacy = field("pc_account_name", "DIRECTOR", "text");
        var group = field("custom_position", "DIRECTOR", "text"); group.setGroup("POSITIONING");
        for (var f : List.of(legacy, group)) {
            assertFalse(MediaAccountFieldPolicy.canWrite(f, account, 10L));
            assertThrows(ServiceException.class, () -> MediaAccountFieldPolicy.validate(List.of(f), Map.of(f.getKey(), "override"), account, 10L));
        }
    }
    @InjectMocks MediaAccountProfileService service;
    @Mock MediaAccountMapper mapper;
    @Mock MediaAccountDiagnosisReminderService diagnosisReminders;
    @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper diagnosisTasks;
    @Mock MediaAccountProfileEntryMapper entries;
    @Mock MediaAccountFieldConfigService configs;
    @Mock MediaAccountService accounts;
    @Mock MediaAccountObjectPermissionProvider objects;
    @Mock PermissionApi permissionApi;
    @Mock AdminUserApi users;
    @Mock PersonMapper people;
    @Mock FileApi fileApi;
    @Mock cn.iocoder.yudao.module.system.api.dict.DictDataApi diagnosisDicts;
    @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper positioningSubmissions;
    @Mock cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService businessTaskCommandService;
    MediaAccountDO account;
    VersionVO config;

    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "profile-test"), MediaAccountDO.class);
        TenantContextHolder.setTenantId(7L);
        account=new MediaAccountDO().setId(1L).setVersion(3).setDirectorUserId(10L).setOwnerOperatorUserId(20L);
        config=new VersionVO();config.setId(8L);config.setFields(List.of(field("goal","DIRECTOR","text"),field("nickname","OPERATOR","text"),field("stage","AUTO","text"),field("cover","UNASSIGNED","image"),field("review","DIRECTOR","record")));
    }
    @AfterEach void clearTenant(){TenantContextHolder.clear();}
    static FieldVO field(String key,String owner,String type) {
        var f=new FieldVO();f.setKey(key);f.setLabel(key);f.setType(type);f.setOwnerType(owner);f.setGroup("PROFILE");f.setEnabled(true);f.setRequiredForComplete(true);return f;
    }
    void writable(Long user){
        when(mapper.selectByIdForUpdate(1L,7L)).thenReturn(account);
        when(objects.hasPermission(1L,"edit",user)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(user,"zsjos:media-account:edit","zsjos:media-account:maintenance")).thenReturn(true);
        when(configs.getPublished()).thenReturn(config);
    }
    Patch patch(Map<String,Object> changes){var r=new Patch();r.setVersion(3);r.setConfigVersionId(8L);r.setIdempotencyKey("test-command");r.setChanges(changes);return r;}
    @Test void policyMatrixFailsClosedForBothOwnersAndUnknownKeys(){
        for(var f:config.getFields()) for(Long user:List.of(10L,20L,30L)) {
            boolean allowed=f.getOwnerType().equals("DIRECTOR")&&user==10L||f.getOwnerType().equals("OPERATOR")&&user==20L;
            assertEquals(allowed,MediaAccountFieldPolicy.canWrite(f,account,user));
            if(!allowed || "record".equals(f.getType())) assertThrows(ServiceException.class,()->MediaAccountFieldPolicy.validate(config.getFields(),Map.of(f.getKey(),"x"),account,user));
        }
        assertThrows(ServiceException.class,()->MediaAccountFieldPolicy.validate(config.getFields(),Map.of("unknown","x"),account,10L));
        var disabled=field("disabled","DIRECTOR","text");disabled.setEnabled(false);
        assertFalse(MediaAccountFieldPolicy.canWrite(disabled,account,10L));
        assertFalse(MediaAccountFieldPolicy.empty(false));assertFalse(MediaAccountFieldPolicy.empty(0));
    }
    @Test void permissionFailureCannotMutate(){
        when(mapper.selectByIdForUpdate(1L,7L)).thenReturn(account);
        assertEquals(MEDIA_ACCOUNT_PERMISSION_DENIED.getCode(),assertThrows(ServiceException.class,()->service.patch(1L,patch(Map.of("goal","x")),10L)).getCode());
        verifyNoInteractions(entries,configs);
    }
    @Test void crossTenantAccountIsNotFound(){
        assertEquals(MEDIA_ACCOUNT_NOT_EXISTS.getCode(),assertThrows(ServiceException.class,()->service.patch(1L,patch(Map.of()),10L)).getCode());
        verify(mapper).selectByIdForUpdate(1L,7L);verifyNoInteractions(entries);
    }
    @Test void staleAccountAndConfigReturnDistinctConflicts(){
        writable(10L);
        var r=patch(Map.of("goal","x"));r.setVersion(2);
        assertEquals(MEDIA_ACCOUNT_VERSION_CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.patch(1L,r,10L)).getCode());
        r.setVersion(3);r.setConfigVersionId(4L);
        assertEquals(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.patch(1L,r,10L)).getCode());
        verify(entries,never()).insert(any(MediaAccountProfileEntryDO.class));
    }
    @Test void directorPatchPreservesOperatorAndDisabledHistoricalFields(){
        writable(10L);account.setNickname("existing").setDetailValuesJson("{\"nickname\":\"existing\",\"retired\":\"historic\"}");
        when(configs.validateAndSnapshot(anyMap(),anyList())).thenAnswer(call -> new MediaAccountFieldConfigService.DetailSnapshot(8L,call.getArgument(0),List.of()));
        when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        assertEquals(4,service.patch(1L,patch(Map.of("goal","new")),10L));
        assertEquals("existing",account.getNickname());assertTrue(account.getDetailValuesJson().contains("historic"));
        assertTrue(account.getDetailValuesJson().contains("new"));
        verify(entries).insert(argThat((MediaAccountProfileEntryDO e)->"PROFILE".equals(e.getKind())&&e.getResultVersion()==4));
    }
    @Test void operatorCanExplicitlyClearNicknameWithoutTouchingDirector(){
        writable(20L);account.setNickname("before").setDetailValuesJson("{\"goal\":\"keep\"}");
        when(configs.validateAndSnapshot(anyMap(),anyList())).thenReturn(new MediaAccountFieldConfigService.DetailSnapshot(8L,Map.of("goal","keep"),List.of()));
        when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        Map<String,Object> changes=new HashMap<>();changes.put("nickname",null);
        assertEquals(4,service.patch(1L,patch(changes),20L));assertNull(account.getNickname());assertTrue(account.getDetailValuesJson().contains("keep"));
    }
    @Test void retryReplaysButReusedKeyWithDifferentPayloadFails(){
        writable(10L);
        when(configs.validateAndSnapshot(anyMap(),anyList())).thenReturn(new MediaAccountFieldConfigService.DetailSnapshot(8L,Map.of("goal","x"),List.of()));
        when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var r=patch(Map.of("goal","x"));assertEquals(4,service.patch(1L,r,10L));
        var capture=ArgumentCaptor.forClass(MediaAccountProfileEntryDO.class);verify(entries).insert(capture.capture());
        when(entries.replay(1L,10L,"test-command")).thenReturn(capture.getValue());account.setVersion(4);
        assertEquals(4,service.patch(1L,r,10L));r.setChanges(Map.of("goal","different"));
        assertEquals(MEDIA_ACCOUNT_PROFILE_IDEMPOTENCY_CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.patch(1L,r,10L)).getCode());
        verify(entries,times(1)).insert(any(MediaAccountProfileEntryDO.class));
    }
    @Test void missingUsesConfigurationNotOnlyStoredSnapshots(){
        when(accounts.require(1L)).thenReturn(account);when(configs.getPublished()).thenReturn(config);
        var r=service.get(1L,30L);
        assertEquals(List.of("goal","nickname"),r.getMissingFields());assertEquals(Map.of("DIRECTOR",1,"OPERATOR",1),r.getMissingByOwner());assertTrue(r.getEditableFields().isEmpty());
    }
    @Test void appendCreatesImmutableRecordAndAdvancesAccountVersion(){
        writable(10L);when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var r=new RecordRequest();r.setVersion(3);r.setConfigVersionId(8L);r.setIdempotencyKey("record");r.setFieldKey("review");r.setContent("diagnosis");
        assertEquals(4,service.append(1L,r,10L));verify(entries).insert(argThat((MediaAccountProfileEntryDO e)->"RECORD".equals(e.getKind())&&"diagnosis".equals(e.getContent())&&"review".equals(e.getFieldKey())));
        verify(entries,never()).updateById(any(MediaAccountProfileEntryDO.class));
    }
    @Test void invalidOrForeignAttachmentIsRejectedBeforeAppend(){
        writable(10L);
        var r=new RecordRequest();r.setVersion(3);r.setConfigVersionId(8L);r.setIdempotencyKey("record");r.setFieldKey("review");r.setContent("diagnosis");r.setFileIds(List.of(999L));
        assertEquals(MEDIA_ACCOUNT_ATTACHMENT_INVALID.getCode(),assertThrows(ServiceException.class,()->service.append(1L,r,10L)).getCode());verify(entries,never()).insert(any(MediaAccountProfileEntryDO.class));
    }
    @Test void sameTenantDifferentAccountFileCannotBeBound() {
        writable(10L);
        var file=new cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO();
        file.setId(999L);file.setCreator("10");file.setPath("zsjos/media-account/7/2/10/file.pdf");
        when(fileApi.getFileInfo(999L)).thenReturn(file);
        var r=new RecordRequest();r.setVersion(3);r.setConfigVersionId(8L);r.setIdempotencyKey("record");r.setFieldKey("review");r.setContent("diagnosis");r.setFileIds(List.of(999L));
        assertEquals(MEDIA_ACCOUNT_ATTACHMENT_INVALID.getCode(),assertThrows(ServiceException.class,()->service.append(1L,r,10L)).getCode());
        verify(entries,never()).insert(any(MediaAccountProfileEntryDO.class));
    }
    @Test void appearanceTextCannotUploadFiles() {
        when(accounts.require(1L)).thenReturn(account);
        when(objects.hasPermission(1L,"edit",20L)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L,"zsjos:media-account:edit","zsjos:media-account:maintenance")).thenReturn(true);
        config.setFields(List.of(field("avatar","OPERATOR","textarea"),field("background","OPERATOR","textarea")));
        when(configs.getPublished()).thenReturn(config);
        for (String key : List.of("avatar", "background"))
            assertEquals(MEDIA_ACCOUNT_ATTACHMENT_INVALID.getCode(),assertThrows(ServiceException.class,
                ()->service.upload(1L,key,new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff},"image.jpg","image/jpeg",20L)).getCode());
        verifyNoInteractions(fileApi);
    }
    @Test void invalidImageBytesNeverReachFileStorage() {
        when(accounts.require(1L)).thenReturn(account);
        when(objects.hasPermission(1L,"edit",20L)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L,"zsjos:media-account:edit","zsjos:media-account:maintenance")).thenReturn(true);
        config.setFields(List.of(field("avatar","OPERATOR","image")));when(configs.getPublished()).thenReturn(config);
        assertEquals(MEDIA_ACCOUNT_ATTACHMENT_INVALID.getCode(),assertThrows(ServiceException.class,()->service.upload(1L,"avatar","<html>".getBytes(),"fake.png","image/png",20L)).getCode());
        verifyNoInteractions(fileApi);
    }
    @Test void operatorCanUploadCoverThroughConfiguredResponsibility() {
        when(accounts.require(1L)).thenReturn(account);
        when(objects.hasPermission(1L,"edit",20L)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L,"zsjos:media-account:edit","zsjos:media-account:maintenance")).thenReturn(true);
        config.setFields(List.of(field("cover","OPERATOR","image")));
        when(configs.getPublished()).thenReturn(config);
        byte[] png={(byte)0x89,'P','N','G',13,10,26,10};
        var info=new cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO();
        info.setId(99L);info.setName("cover.png");info.setType("image/png");
        info.setCreator("20");info.setPath("zsjos/media-account/7/1/20/cover.png");
        when(fileApi.createFileInfo(png,"cover.png","zsjos/media-account/7/1/20","image/png")).thenReturn(info);
        when(fileApi.getFileInfo(99L)).thenReturn(info);
        assertEquals(99L,service.upload(1L,"cover",png,"cover.png","image/png",20L).getId());
    }
    @Test void directorCannotUploadOrClearOperatorCover() {
        writable(10L);
        config.setFields(List.of(field("cover","OPERATOR","image")));
        when(accounts.require(1L)).thenReturn(account);
        assertEquals(MEDIA_ACCOUNT_PERMISSION_DENIED.getCode(),assertThrows(ServiceException.class,
            ()->service.upload(1L,"cover",new byte[]{1},"cover.png","image/png",10L)).getCode());
        Map<String,Object> clear=new HashMap<>();clear.put("cover",null);
        assertEquals(MEDIA_ACCOUNT_PERMISSION_DENIED.getCode(),assertThrows(ServiceException.class,
            ()->service.patch(1L,patch(clear),10L)).getCode());
        verifyNoInteractions(fileApi);
        verify(entries,never()).insert(any(MediaAccountProfileEntryDO.class));
    }
    @Test void operatorCanClearCoverWithoutChangingOtherFields() {
        writable(20L);
        config.setFields(List.of(field("cover","OPERATOR","image")));
        account.setDetailValuesJson("{\"cover\":99,\"goal\":\"keep\"}");
        when(configs.validateAndSnapshot(anyMap(),anyList())).thenAnswer(call ->
            new MediaAccountFieldConfigService.DetailSnapshot(8L,call.getArgument(0),List.of()));
        when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        Map<String,Object> clear=new HashMap<>();clear.put("cover",null);
        assertEquals(4,service.patch(1L,patch(clear),20L));
        assertFalse(account.getDetailValuesJson().contains("cover"));
        assertTrue(account.getDetailValuesJson().contains("keep"));
    }
    @Test void versionCasFailureDoesNotAppendAuditEntry() {
        writable(10L);
        when(configs.validateAndSnapshot(anyMap(),anyList())).thenReturn(new MediaAccountFieldConfigService.DetailSnapshot(8L,Map.of("goal","x"),List.of()));
        assertEquals(MEDIA_ACCOUNT_VERSION_CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.patch(1L,patch(Map.of("goal","x")),10L)).getCode());
        verify(entries,never()).insert(any(MediaAccountProfileEntryDO.class));
    }
    @Test void historyUsesSavedLabelsAndNeverUpdatesExistingEntries(){
        when(accounts.require(1L)).thenReturn(account);
        var row=new MediaAccountProfileEntryDO();row.setId(1L);row.setTitle("original title");row.setOperatedByName("original name");row.setSnapshotJson("[{\"key\":\"level\",\"displayValue\":\"original label\"}]");
        when(entries.page(eq(1L),any())).thenReturn(new PageResult<>(List.of(row),1L));
        var r=service.history(1L,new PageParam(),10L);assertEquals("original label",r.getList().getFirst().getSnapshots().getFirst().getDisplayValue());verifyNoInteractions(configs,users);
    }

    DiagnosisRequest diagnosis(String type) {
        var r = new DiagnosisRequest(); r.setTemplateType(type); r.setCycle(type.equals("diagnosis_initial") ? 0 : 1);
        r.setVersion(3); r.setConfigVersionId(8L); r.setIdempotencyKey("diagnosis-command");
        r.setCurrentStage("S2"); r.setAccountStatus("active"); r.setPrimaryProblem("content");
        r.setPrimaryProblemEvidence("已分析当前内容"); r.setConclusion("先改善内容表达"); r.setReposition(false);
        r.setCooperationLevel("good"); r.setCooperationEvidence("按时配合"); r.setSecondaryProblem("content");
        r.setSecondaryProblemEvidence("表现记录"); r.setImprovementMeasures("优化开头"); r.setObservedData("留存率");
        return r;
    }
    cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO applied() {
        account.setStudentPersonId(11L).setCreateServiceRelationId(12L);
        var s = new cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO();
        s.setTenantId(7L); s.setStudentPersonId(11L); s.setServiceRelationId(12L); s.setSubmissionNo(2);
        when(positioningSubmissions.selectCurrentConfirmedByAccount(1L)).thenReturn(s); return s;
    }
    void dictionaries() {
        when(diagnosisDicts.getDictDataList(anyString())).thenAnswer(call -> {
            String type = call.getArgument(0);
            String code = type.endsWith("stage") ? "S2" : type.endsWith("current_status") ? "active" : type.endsWith("cooperation_level") ? "good" : "content";
            var d = new cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO();
            d.setValue(code); d.setLabel("历史标签-" + code); return List.of(d);
        });
    }
    @Test void initialDiagnosisFreezesLabelsAndDoesNotCompletePeriodicTask() {
        writable(10L); applied(); dictionaries(); when(mapper.update(isNull(),any())).thenReturn(1);
        assertEquals(4,service.submitDiagnosis(1L,diagnosis("diagnosis_initial"),10L));
        var capture=ArgumentCaptor.forClass(MediaAccountProfileEntryDO.class); verify(entries).insert(capture.capture());
        var e=capture.getValue(); assertEquals("DIAGNOSIS",e.getKind()); assertEquals("diagnosis_initial",e.getFieldKey());
        assertTrue(e.getSnapshotJson().contains("历史标签-S2")); assertTrue(e.getContent().contains("先改善内容表达"));
        verifyNoInteractions(businessTaskCommandService);
    }
    @Test void initialRequiresAppliedSnapshotInSameTenantStudentAndService() {
        writable(10L); var req=diagnosis("diagnosis_initial");
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        var s=applied();
        s.setTenantId(8L); assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        s.setTenantId(7L); s.setStudentPersonId(99L); assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        s.setStudentPersonId(11L); s.setServiceRelationId(99L); assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        verify(mapper,never()).update(isNull(),any()); verifyNoInteractions(diagnosisDicts,businessTaskCommandService);
    }
    @Test void existingDiagnosisRejectsNewInitialAndPeriodicRequiresInitial() {
        writable(10L);
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,diagnosis("diagnosis_7d"),10L));
        when(entries.latestDiagnosis(1L)).thenReturn(new MediaAccountProfileEntryDO());
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,diagnosis("diagnosis_initial"),10L));
        verify(mapper,never()).update(isNull(),any());
    }
    @Test void maintenancePermissionDoesNotLetOperatorDiagnose() {
        when(mapper.selectByIdForUpdate(1L,7L)).thenReturn(account);
        when(objects.hasPermission(1L,"edit",20L)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(20L,"zsjos:media-account:edit","zsjos:media-account:maintenance")).thenReturn(true);
        assertEquals(MEDIA_ACCOUNT_PERMISSION_DENIED.getCode(),assertThrows(ServiceException.class,
            ()->service.submitDiagnosis(1L,diagnosis("diagnosis_initial"),20L)).getCode());
        verifyNoInteractions(entries,diagnosisDicts,businessTaskCommandService);
    }
    @Test void diagnosisRetryDoesNotOverwriteNewerState() {
        writable(10L); applied(); dictionaries(); when(mapper.update(isNull(),any())).thenReturn(1);
        var req=diagnosis("diagnosis_initial"); service.submitDiagnosis(1L,req,10L);
        var capture=ArgumentCaptor.forClass(MediaAccountProfileEntryDO.class); verify(entries).insert(capture.capture());
        when(entries.replay(1L,10L,"diagnosis-command")).thenReturn(capture.getValue());
        account.setVersion(9);
        assertEquals(4,service.submitDiagnosis(1L,req,10L)); verify(mapper,times(1)).update(isNull(),any());
        req.setConclusion("不同请求"); assertEquals(MEDIA_ACCOUNT_PROFILE_IDEMPOTENCY_CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L)).getCode());
    }
    @Test void earlyPeriodicSubmissionCompletesOnlyItsCycleAndStoresNewLabels() {
        writable(10L); dictionaries(); when(entries.latestDiagnosis(1L)).thenReturn(new MediaAccountProfileEntryDO());
        when(mapper.update(isNull(),any())).thenReturn(1);
        var todo = new DiagnosisTodo(80L,1L,1L,"account","14天", "diagnosis_14d",2,java.time.LocalDateTime.now().plusDays(7),Map.of("requirementSnapshot", Map.of("diagnosis_14d","冻结要求")));
        when(diagnosisReminders.accountTasks(1L,10L)).thenReturn(List.of(todo));
        var task = new cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO();
        task.setId(80L); task.setBizId(1L); task.setAssigneeId(10L); task.setStatus("pending"); task.setIdempotencyKey("media-diagnosis-v2:1:1:media_account_diagnosis_14d:2");
        when(diagnosisTasks.selectByIdForUpdate(80L,7L)).thenReturn(task);
        when(businessTaskCommandService.completeByKey(anyString(),any())).thenReturn(true);
        var r=diagnosis("diagnosis_14d"); r.setCycle(2);
        assertEquals(4,service.submitDiagnosis(1L,r,10L));
        verify(businessTaskCommandService).completeByKey(eq("media-diagnosis-v2:1:1:media_account_diagnosis_14d:2"),any());
        verifyNoMoreInteractions(businessTaskCommandService);
    }
    @Test void diagnosisVersionConflictAndInvalidDictionaryDoNotWrite() {
        writable(10L); var req=diagnosis("diagnosis_initial"); req.setVersion(2);
        assertEquals(MEDIA_ACCOUNT_VERSION_CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L)).getCode());
        req.setVersion(3); applied();
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        verify(mapper,never()).update(isNull(),any()); verifyNoInteractions(businessTaskCommandService);
    }
    @Test void initialEvidenceAndConclusionAreRequiredAtHttpBoundary() {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var req=diagnosis("diagnosis_initial"); req.setPrimaryProblemEvidence(" "); req.setConclusion("x".repeat(2001));
            var keys=factory.getValidator().validate(req).stream().map(v->v.getPropertyPath().toString()).toList();
            assertTrue(keys.contains("primaryProblemEvidence")); assertTrue(keys.contains("conclusion"));
        }
    }

    @Test void profileUsesSavedDiagnosisLabelsWithoutLookingUpCurrentDictionary() {
        when(accounts.require(1L)).thenReturn(account); when(configs.getPublished()).thenReturn(config);
        account.setSStage("S2").setCurrentStatusValue("active").setPrimaryProblemCodeValue("content");
        var entry=new MediaAccountProfileEntryDO(); entry.setFieldKey("diagnosis_initial");
        entry.setSnapshotJson("[{\"key\":\"stage\",\"displayValue\":\"提交时阶段名称\"}]");
        when(entries.latestDiagnosis(1L)).thenReturn(entry);
        var result=service.get(1L,30L);
        assertEquals("S2",result.getValues().get("stage")); assertEquals("启动诊断",result.getSourceNotes().get("stage"));
        assertEquals("提交时阶段名称",result.getSnapshots().getFirst().getDisplayValue());
        assertFalse(result.isCanStartDiagnosis()); assertFalse(result.isCanSubmitDiagnosis());
        verifyNoInteractions(diagnosisDicts);
    }
    @Test void initialSecondaryIsOptionalButSelectionAndEvidenceMustBePaired() {
        writable(10L); applied(); dictionaries(); when(mapper.update(isNull(),any())).thenReturn(1);
        var req=diagnosis("diagnosis_initial"); req.setSecondaryProblemEvidence(" ");
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        req.setSecondaryProblem(null); req.setSecondaryProblemEvidence("orphan evidence");
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        req.setSecondaryProblemEvidence(null);
        assertEquals(4,service.submitDiagnosis(1L,req,10L));
        verify(entries).insert(argThat((MediaAccountProfileEntryDO e)-> !e.getContent().contains("secondaryProblemLabel")));
    }
    @Test void initialSecondaryStoresDictionaryLabelAndRejectsUnknownSelection() {
        writable(10L); applied(); dictionaries(); when(mapper.update(isNull(),any())).thenReturn(1);
        var req=diagnosis("diagnosis_initial"); req.setSecondaryProblem("unknown");
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        req.setSecondaryProblem("content"); service.submitDiagnosis(1L,req,10L);
        verify(entries).insert(argThat((MediaAccountProfileEntryDO e)->
            e.getContent().contains("\"secondaryProblemLabel\":\"历史标签-content\"")));
    }
    @Test void periodicRevisionCannotChangeRoundAndRetainsSecondarySnapshot() {
        writable(10L); dictionaries(); when(mapper.update(isNull(),any())).thenReturn(1);
        var previous=new MediaAccountProfileEntryDO(); previous.setId(42L); previous.setKind("DIAGNOSIS");
        previous.setContent("{\"cycle\":2,\"secondaryProblem\":\"content\",\"secondaryProblemLabel\":\"原次要瓶颈标签\"}");
        when(entries.latestField(1L,"diagnosis_14d")).thenReturn(previous);
        when(entries.latestDiagnosis(1L)).thenReturn(previous);
        var req=diagnosis("diagnosis_14d"); req.setPreviousEntryId(42L);
        assertThrows(ServiceException.class,()->service.submitDiagnosis(1L,req,10L));
        verify(mapper,never()).update(isNull(),any());
        req.setCycle(2); service.submitDiagnosis(1L,req,10L);
        verify(entries).insert(argThat((MediaAccountProfileEntryDO e)-> e.getContent().contains("原次要瓶颈标签")));
        verify(businessTaskCommandService,never()).completeByKey(anyString(),any());
    }

    @Test void automaticGoalCannotBeManuallyPatchedAndCommitmentsAreDirectorOnly() {
        for (String key : List.of("delivery_goals","diagnosis_7d_requirement","diagnosis_14d_requirement","diagnosis_28d_requirement")) {
            var f=field(key,"DIRECTOR","textarea");
            assertFalse(MediaAccountFieldPolicy.canWrite(f,account,10L));
            assertThrows(ServiceException.class,()->MediaAccountFieldPolicy.validate(List.of(f),Map.of(key,"override"),account,10L));
        }
        for (String key : List.of("student_commitments","company_commitments")) {
            assertTrue(MediaAccountFieldPolicy.canWrite(field(key,"DIRECTOR","textarea"),account,10L));
            assertFalse(MediaAccountFieldPolicy.canWrite(field(key,"OPERATOR","textarea"),account,20L));
        }
    }
}
