package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.MediaAccountDiagnosisController;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisBatchVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDiagnosisBatchServiceTest {
    @InjectMocks AccountDiagnosisBatchService service;
    @Mock MediaAccountMapper accounts;
    @Mock MediaAccountObjectPermissionProvider objects;
    @Mock MediaAccountProfileService profiles;
    @Mock WeeklyDiagnosisService weekly;
    @Mock MediaAccountProfileEntryMapper entries;
    Map<Long, MediaAccountDO> records;
    Map<String, MediaAccountProfileEntryDO> written;
    LocalDateTime due = LocalDateTime.of(2026, 11, 8, 0, 0);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        records = new HashMap<>(); written = new HashMap<>();
        for (long id : List.of(10L, 11L)) records.put(id, new MediaAccountDO().setId(id).setVersion(1)
                .setStudentPersonId(2L).setCreateServiceRelationId(3L).setDirectorUserId(20L)
                .setDetailValuesJson(JsonUtils.toJsonString(Map.of("_diagnosisContext", "{\"roundKey\":\"round\"}"))));
        lenient().when(accounts.selectById(anyLong())).thenAnswer(i -> records.get(i.getArgument(0)));
        lenient().when(accounts.selectByIdForUpdate(anyLong(), eq(1L))).thenAnswer(i -> records.get(i.getArgument(0)));
        lenient().when(objects.hasPermission(anyLong(), anyString(), eq(20L))).thenReturn(true);
        lenient().when(entries.replay(anyLong(), eq(20L), anyString())).thenAnswer(i -> written.get(i.getArgument(0) + ":" + i.getArgument(2)));
    }
    @AfterEach void clear() {TenantContextHolder.clear();}

    MediaAccountProfileVO.DiagnosisRequest common(long id) {
        var common = new MediaAccountProfileVO.DiagnosisRequest();
        common.setTemplateType("diagnosis_initial"); common.setCycle(0); common.setVersion(1); common.setConfigVersionId(1L);
        common.setCurrentStage(id == 10 ? "S1" : "S3"); common.setAccountStatus(id == 10 ? "normal" : "testing");
        common.setIdempotencyKey("placeholder"); common.setConclusion("共用结论"); return common;
    }
    Submit initial() {
        var request = new Submit();request.setAccountId(10L);request.setTemplateType("diagnosis_initial");request.setIdempotencyKey("batch-key");
        request.setAccounts(List.of(11L,10L).stream().map(id -> {var row=new Item();row.setAccountId(id);row.setInitial(common(id));return row;}).toList());
        return request;
    }
    WeeklyDiagnosisVO.Node node(long id, String round, LocalDateTime deadline) {
        return new WeeklyDiagnosisVO.Node(WeeklyDiagnosisPeriods.key(id,round,deadline),round,2,deadline,List.of(),List.of());
    }
    Submit periodic() {
        var request = initial();request.setTemplateType("weekly");request.setNodeKey(node(10,"round",due).key());
        for(var row:request.getAccounts()) {
            var command=new WeeklyDiagnosisVO.Submit();command.setCommon(row.getInitial());command.setNodeKey(node(row.getAccountId(),"round",due).key());
            command.getCommon().setTemplateType("diagnosis_7d");command.getCommon().setCycle(2);
            command.setHighestLikes(row.getAccountId()==10L?0L:99L);command.setMetricToken("metrics-"+row.getAccountId());
            row.setInitial(null);row.setWeekly(command);
        }
        return request;
    }
    Integer save(long id, MediaAccountProfileVO.DiagnosisRequest common) {
        var saved=new MediaAccountProfileEntryDO();saved.setId(id);saved.setAccountId(id);saved.setContent("{}");saved.setResultVersion(2);
        written.put(id+":"+common.getIdempotencyKey(),saved);return 2;
    }
    void succeedingInitial() {when(profiles.submitDiagnosis(anyLong(),any(),eq(20L))).thenAnswer(i -> save(i.getArgument(0),i.getArgument(1)));}

    @Test void separateStatesAndStableSortedLocksWithSharedContent() {
        succeedingInitial();var request=initial();var original=JsonUtils.toJsonString(request);
        var result=service.submit(request,20L);
        assertEquals(List.of(10L,11L),result.stream().map(Result::accountId).toList());
        var order=inOrder(accounts);order.verify(accounts).selectByIdForUpdate(10L,1L);order.verify(accounts).selectByIdForUpdate(11L,1L);
        var commands=ArgumentCaptor.forClass(MediaAccountProfileVO.DiagnosisRequest.class);
        verify(profiles,times(2)).submitDiagnosis(anyLong(),commands.capture(),eq(20L));
        assertEquals(List.of("S1","S3"),commands.getAllValues().stream().map(MediaAccountProfileVO.DiagnosisRequest::getCurrentStage).toList());
        assertEquals(List.of("normal","testing"),commands.getAllValues().stream().map(MediaAccountProfileVO.DiagnosisRequest::getAccountStatus).toList());
        assertEquals(2,commands.getAllValues().stream().map(MediaAccountProfileVO.DiagnosisRequest::getIdempotencyKey).distinct().count());
        assertEquals(original,JsonUtils.toJsonString(request));assertTrue(written.values().stream().allMatch(e->e.getContent().contains("diagnosisBatchAccountIds")));
    }
    @Test void identicalRetryReturnsVersionsWithoutWritingAndChangedContentConflicts() {
        succeedingInitial();var request=initial();var first=service.submit(request,20L);assertEquals(first,service.submit(request,20L));
        verify(profiles,times(2)).submitDiagnosis(anyLong(),any(),eq(20L));
        request.getAccounts().getFirst().getInitial().setConclusion("changed");
        assertThrows(RuntimeException.class,()->service.submit(request,20L));
        verify(profiles,times(2)).submitDiagnosis(anyLong(),any(),eq(20L));
    }
    @Test void emptyOrDuplicateSelectionCannotWrite() {
        var request=initial();request.setAccounts(List.of());assertThrows(RuntimeException.class,()->service.submit(request,20L));
        var row=initial().getAccounts().getFirst();request.setAccounts(List.of(row,row));assertThrows(RuntimeException.class,()->service.submit(request,20L));
        verifyNoInteractions(profiles,weekly);
    }
    @Test void unauthorizedSecondAccountFailsBeforeAnyDiagnosis() {
        when(objects.hasPermission(11L,"edit",20L)).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.submit(initial(),20L));verifyNoInteractions(profiles,weekly);
    }
    @Test void foreignServiceStudentOrDirectorFailsBeforeAnyDiagnosis() {
        for(var row:List.of(new MediaAccountDO().setId(11L).setDirectorUserId(20L).setStudentPersonId(99L).setCreateServiceRelationId(3L),
                new MediaAccountDO().setId(11L).setDirectorUserId(20L).setStudentPersonId(2L).setCreateServiceRelationId(99L),
                new MediaAccountDO().setId(11L).setDirectorUserId(99L).setStudentPersonId(2L).setCreateServiceRelationId(3L))) {
            records.put(11L,row);assertThrows(RuntimeException.class,()->service.submit(initial(),20L));
        }
        verifyNoInteractions(profiles,weekly);
    }
    @Test void mixedInitialWeeklyOrHistoryRevisionIsRejected() {
        var request=initial();request.getAccounts().getFirst().setWeekly(new WeeklyDiagnosisVO.Submit());
        assertThrows(RuntimeException.class,()->service.submit(request,20L));
        var revision=initial();revision.getAccounts().getFirst().getInitial().setPreviousEntryId(9L);
        assertThrows(RuntimeException.class,()->service.submit(revision,20L));verifyNoInteractions(profiles,weekly);
    }
    @Test void periodicDifferentRoundOrDeadlineIsRejectedBeforeWriting() {
        for(var candidate:List.of(node(11,"other",due),node(11,"round",due.plusDays(7)))) {
            var request=periodic();request.getAccounts().getFirst().getWeekly().setNodeKey(candidate.key());
            when(weekly.overview(10L,20L)).thenReturn(new WeeklyDiagnosisVO.Overview(List.of(),List.of(node(10,"round",due))));
            when(weekly.overview(11L,20L)).thenReturn(new WeeklyDiagnosisVO.Overview(List.of(),List.of(candidate)));
            assertThrows(RuntimeException.class,()->service.submit(request,20L));
        }
        verify(weekly,never()).submit(anyLong(),any(),anyLong());verifyNoInteractions(profiles);
    }
    @Test void periodicBatchKeepsEachNodeMetricsAndStateIndependent() {
        var request=periodic();String original=JsonUtils.toJsonString(request);
        for(long id:List.of(10L,11L))when(weekly.overview(id,20L)).thenReturn(new WeeklyDiagnosisVO.Overview(List.of(),List.of(node(id,"round",due))));
        when(weekly.submit(anyLong(),any(),eq(20L))).thenAnswer(i->{
            WeeklyDiagnosisVO.Submit command=i.getArgument(1);return save(i.getArgument(0),command.getCommon());
        });
        assertEquals(2,service.submit(request,20L).size());
        var commands=ArgumentCaptor.forClass(WeeklyDiagnosisVO.Submit.class);
        verify(weekly,times(2)).submit(anyLong(),commands.capture(),eq(20L));
        assertEquals(List.of(0L,99L),commands.getAllValues().stream().map(WeeklyDiagnosisVO.Submit::getHighestLikes).toList());
        assertEquals(List.of(node(10,"round",due).key(),node(11,"round",due).key()),commands.getAllValues().stream().map(WeeklyDiagnosisVO.Submit::getNodeKey).toList());
        assertEquals(List.of("S1","S3"),commands.getAllValues().stream().map(c->c.getCommon().getCurrentStage()).toList());
        assertEquals(original,JsonUtils.toJsonString(request));verifyNoInteractions(profiles);
    }
    @Test void candidateQueryFiltersAuthorizationAndUsesServiceAndMatchingNode() {
        when(accounts.selectList(any(Wrapper.class))).thenReturn(List.of(records.get(10L),records.get(11L)));
        var profile=new MediaAccountProfileVO();var config=new MediaAccountFieldConfigRespVO.VersionVO();config.setId(1L);profile.setConfig(config);profile.setCanSubmitDiagnosis(true);
        when(profiles.get(anyLong(),eq(20L))).thenReturn(profile);
        when(weekly.overview(10L,20L)).thenReturn(new WeeklyDiagnosisVO.Overview(List.of(),List.of(node(10,"round",due))));
        when(weekly.overview(11L,20L)).thenReturn(new WeeklyDiagnosisVO.Overview(List.of(),List.of(node(11,"round",due.plusDays(7)))));
        var candidates=service.candidates(10L,"weekly",node(10,"round",due).key(),20L);
        assertTrue(candidates.getFirst().eligible());assertFalse(candidates.get(1).eligible());assertNotNull(candidates.get(1).reason());
        when(objects.hasPermission(11L,"read",20L)).thenReturn(false);
        assertEquals(1,service.candidates(10L,"weekly",node(10,"round",due).key(),20L).size());
    }
    @Test void transactionRollsBackEarlierAccountAndTaskOnLaterFailure() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:diagnosis_batch_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE batch_probe (account_id BIGINT, kind VARCHAR(20))");
        when(profiles.submitDiagnosis(anyLong(),any(),eq(20L))).thenAnswer(i -> {
            long id=i.getArgument(0);jdbc.update("INSERT INTO batch_probe VALUES (?, 'diagnosis')",id);
            jdbc.update("INSERT INTO batch_probe VALUES (?, 'task')",id);
            if(id==11)throw new IllegalStateException("later account failure");return save(id,i.getArgument(1));
        });
        var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var proxy=(AccountDiagnosisBatchService)factory.getProxy();
        assertThrows(IllegalStateException.class,()->proxy.submit(initial(),20L));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM batch_probe",Integer.class));
    }
    @Test void featureAndObjectPermissionsRemainIndependent() {
        for(var method:MediaAccountDiagnosisController.class.getDeclaredMethods())if(Set.of("batchCandidates","submitBatch").contains(method.getName())) {
            var permission=method.getAnnotation(PreAuthorize.class);assertNotNull(permission);assertTrue(permission.value().contains("zsjos:media-account:maintenance"));
        }
        for(var method:AccountDiagnosisBatchService.class.getDeclaredMethods())if(Set.of("candidates","submit").contains(method.getName())) {
            var permission=method.getAnnotation(ZsjosPermission.class);assertNotNull(permission);assertEquals("edit",permission.action());
        }
    }
}
