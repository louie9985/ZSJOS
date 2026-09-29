package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.*;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import jakarta.annotation.Resource;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/** Real creation/dispatch/follow-up/qualification/task services and MyBatis mappers on isolated H2.
 * Remote system/catalog APIs are mocked; MySQL bit literal syntax alone is adapted for H2. */
class LeadSelfSourcedTransactionTest {
    private final Map<Class<?>,Object> beans=new HashMap<>();
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private LeadSubmissionService submission;
    private LeadFollowUpService followUp;
    private LeadSelfSourcedAutomationService automation;
    private final AtomicInteger sent=new AtomicInteger();
    private final AtomicReference<String> denied=new AtomicReference<>();
    private ZsjosPermissionAspect permissionAspect;

    @Intercepts(@Signature(type=StatementHandler.class,method="prepare",args={Connection.class,Integer.class}))
    public static class H2Bits implements Interceptor {
        @Override public Object intercept(Invocation invocation) throws Throwable {
            var boundSql=((StatementHandler)invocation.getTarget()).getBoundSql();
            SystemMetaObject.forObject(boundSql).setValue("sql",boundSql.getSql().replaceAll("(?i)b'([01])'","$1"));
            return invocation.proceed();
        }
    }
    @BeforeEach void setup() {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:auto"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        jdbc=new JdbcTemplate(dataSource);transactions=new DataSourceTransactionManager(dataSource);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("auto-test",new SpringManagedTransactionFactory(),dataSource));
        var tenant=new MybatisPlusInterceptor();tenant.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler(){public LongValue getTenantId(){return new LongValue(TenantContextHolder.getRequiredTenantId());}}));
        config.addInterceptor(tenant);config.addInterceptor(new H2Bits());
        List<Class<?>> mappers=List.of(PersonMapper.class,LeadMapper.class,LeadIntendedProductMapper.class,LeadAttachmentMapper.class,LeadAssignmentHistoryMapper.class,LeadFollowUpRecordMapper.class,LeadFollowUpImageMapper.class,OpportunityMapper.class,OpportunityFollowUpRecordMapper.class,OpportunityFollowUpImageMapper.class,BusinessEventMapper.class,BusinessTaskMapper.class);
        mappers.forEach(config::addMapper);
        var template=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        for(Class<?> mapper:mappers)beans.put(mapper,template.getMapper(mapper));
        for(Class<?> type:List.of(PersonDO.class,LeadDO.class,LeadIntendedProductDO.class,LeadAttachmentDO.class,LeadAssignmentHistoryDO.class,LeadFollowUpRecordDO.class,LeadFollowUpImageDO.class,OpportunityDO.class,OpportunityFollowUpRecordDO.class,OpportunityFollowUpImageDO.class,BusinessEventDO.class,BusinessTaskDO.class)) {
            var table=TableInfoHelper.getTableInfo(type);var ddl=new StringBuilder("CREATE TABLE "+table.getTableName()+" (id BIGINT PRIMARY KEY");
            for(var field:table.getFieldList()) {
                var javaType=field.getPropertyType();String sqlType=javaType==String.class?"VARCHAR(16000)":javaType==LocalDateTime.class?"TIMESTAMP":javaType==java.math.BigDecimal.class?"DECIMAL(16,2)":"BIGINT";
                String defaultValue=field.getColumn().equals("deleted")?" DEFAULT 0":field.getColumn().equals("create_time")?" DEFAULT CURRENT_TIMESTAMP":"";
                ddl.append(", ").append(field.getColumn()).append(" ").append(sqlType).append(defaultValue);
            }
            jdbc.execute(ddl.append(")").toString());
        }
        jdbc.execute("CREATE UNIQUE INDEX auto_submission ON zsjos_lead(tenant_id,submission_idempotency_key)");
        for(String table:List.of("zsjos_business_event","zsjos_business_task","zsjos_lead_follow_up_record"))jdbc.execute("CREATE UNIQUE INDEX auto_key_"+table+" ON "+table+"(tenant_id,idempotency_key)");
        permissionAspect=new ZsjosPermissionAspect(List.of(new ZsjosObjectPermissionProvider(){
            public String getBizType(){return "lead";}
            public boolean hasPermission(Long id,String action,Long user){var lead=get(LeadMapper.class).selectById(id);return lead!=null&&Objects.equals(user,lead.getOwnerUserId())&&!action.equals(denied.get());}
        }));
        var taskTarget=new BusinessTaskCommandService();beans.put(BusinessTaskCommandService.class,taskTarget);
        var lifecycle=new LeadLifecycleTaskService();beans.put(LeadLifecycleTaskService.class,lifecycle);
        var dispatch=new LeadDispatchServiceImpl();beans.put(LeadDispatchService.class,proxy(dispatch));
        var followTarget=new LeadFollowUpServiceImpl();followUp=proxy(followTarget);beans.put(LeadFollowUpService.class,followUp);
        var qualification=new LeadQualificationServiceImpl();beans.put(LeadQualificationService.class,proxy(qualification));
        var autoTarget=new LeadSelfSourcedAutomationService();automation=proxy(autoTarget);beans.put(LeadSelfSourcedAutomationService.class,automation);
        var submissionTarget=new LeadSubmissionServiceImpl();submission=proxy(submissionTarget);
        for(Object target:List.of(taskTarget,lifecycle,dispatch,followTarget,qualification,autoTarget,submissionTarget))wire(target);
        login();
        when(get(PermissionApi.class).hasAnyPermissions(eq(1L),any(String.class))).thenReturn(true);
        when(get(DictDataApi.class).getDictDataList(anyString())).thenAnswer(call->{String type=call.getArgument(0);return List.of(dict(type.equals(DICT_FOLLOW_UP_METHOD)?"other":type.equals(DICT_FOLLOW_UP_RESULT)?"interested":type.equals(LeadSalesStageSnapshot.DICT_TYPE)?"pending_contact":type.equals(DICT_SOURCE_CHANNEL)?"channel":"a", "配置标签"));});
        when(get(LeadCategorySnapshotService.class).requireEnabled(anyString())).thenReturn(new LeadCategorySnapshotService.Selection("a","录单分类"));
        var rule=new LeadFollowUpRuleDO();rule.setId(1L);rule.setVersion(1);rule.setFirstFollowUpTimeoutMinutes(60);rule.setQualificationTimeoutMinutes(180);
        when(get(LeadFollowUpRuleService.class).requireEnabledRule()).thenReturn(rule);
        when(get(LeadDuplicateMatcher.class).matchSubmissionWeakRules(any())).thenReturn(new LeadDuplicateMatcher.MatchResult(null,List.of(),"none",null,null,null));
        when(get(ZsjosProductSkuService.class).validateLeadProduct(null,true,null,true)).thenReturn(LeadProductSnapshot.unknown());
        when(get(LeadNumberService.class).next(any())).thenReturn("LD-AUTO-TEST");
        var province=area(990000000,2,1);var city=area(990000001,3,990000000);
        when(get(AreaApi.class).getAreaByParentIdAndSelectionCode(1,"OTHER")).thenReturn(province);
        when(get(AreaApi.class).getAreaByParentIdAndSelectionCode(990000000,"OTHER")).thenReturn(city);
        when(get(PersonIdentityWriteService.class).createNew(any(),any(),any(),any())).thenAnswer(call->{var person=new PersonDO();get(PersonMapper.class).insert(person);return person;});
        doAnswer(call->{TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){sent.incrementAndGet();}});return null;}).when(get(LeadNotifyEventPublisher.class)).publish(anyString(),anyLong(),anyString(),anyLong(),any(),anyMap());
    }
    private void login(){TenantContextHolder.setTenantId(9L);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser().setId(1L).setUserType(2),null,List.of()));}
    private DictDataRespDTO dict(String value,String label){var d=new DictDataRespDTO();d.setValue(value);d.setLabel(label);d.setStatus(0);return d;}
    private AreaRespDTO area(int id,int type,int parent){var a=new AreaRespDTO();a.setId(id);a.setType(type);a.setParentId(parent);a.setStatus(0);a.setSelectionCode("OTHER");a.setName("测试地区");return a;}
    @SuppressWarnings("unchecked") private <T>T get(Class<T> type){return (T)beans.computeIfAbsent(type,key->mock(type));}
    private void wire(Object target){for(var field:target.getClass().getDeclaredFields())if(field.isAnnotationPresent(Resource.class))ReflectionTestUtils.setField(target,field.getName(),get(field.getType()));}
    @SuppressWarnings("unchecked") private <T>T proxy(T target){var factory=new AspectJProxyFactory(target);factory.setProxyTargetClass(true);factory.addAspect(permissionAspect);factory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
    private LeadCreateReqVO request(){var r=new LeadCreateReqVO();r.setName("事务测试客户");r.setMobile("13800138000");r.setProvinceCode("OTHER");r.setCityCode("OTHER");r.setSourceChannel("channel");r.setLeadCategory("a");r.setRemark("  已联系，有意向  ");r.setIdempotencyKey("auto-transaction");var p=new LeadProductReqVO();p.setSpuUnknown(true);p.setSkuUnknown(true);p.setPrimary(true);r.setProducts(List.of(p));return r;}
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @AfterEach void close(){SecurityContextHolder.clearContext();TenantContextHolder.clear();if(jdbc!=null)jdbc.execute("SHUTDOWN");}

    @Test void oneSubmissionCommitsCompleteChainAndReplayDoesNotDuplicate() {
        var req=request();var result=submission.createSelfSourced(req,1L);
        assertEquals("valid",result.getQualificationStatus());assertTrue(result.getAutomaticQualificationApplied());
        assertEquals(1,count("zsjos_lead"));assertEquals(1,count("zsjos_person"));assertEquals(1,count("zsjos_lead_follow_up_record"));assertEquals(1,count("zsjos_opportunity"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_task WHERE status='completed'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_task WHERE status='pending'",Integer.class));
        var lead=get(LeadMapper.class).selectById(result.getLeadId());assertEquals(1L,lead.getSourceUserId());assertEquals(1L,lead.getOwnerUserId());assertEquals("已联系，有意向",lead.getRemark());assertEquals("录单分类",lead.getLeadCategoryLabelSnapshot());assertNotNull(lead.getQualifiedAt());assertNotNull(lead.getLastFollowUpRecordId());
        var history=followUp.getPage(lead.getId(),1,100);assertEquals(1L,history.getTotal());assertEquals(LeadAutomaticGeneration.SOURCE,history.getList().getFirst().getGenerationSource());assertFalse(history.getList().getFirst().getOccurredAt().isBefore(lead.getSubmittedAt()));
        assertEquals(3,sent.get());submission.createSelfSourced(req,1L);assertEquals(3,sent.get());assertEquals(1,count("zsjos_opportunity"));
    }
    @Test void optionalFutureReminderRemainsOrdinaryWork() {
        var req=request();req.setSelfSourcedNextFollowUpAt(LocalDateTime.now().plusDays(1));submission.createSelfSourced(req,1L);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_task WHERE status='pending' AND task_type='lead_follow_up_reminder'",Integer.class));
        String payload=jdbc.queryForObject("SELECT payload FROM zsjos_business_task WHERE task_type='lead_follow_up_reminder'",String.class);assertFalse(LeadAutomaticGeneration.isAutomatic(payload));
    }
    @Test void qualificationPermissionFailureRollsBackLeadFirstFollowTasksAndNotifications() {
        denied.set("qualify");assertThrows(IllegalArgumentException.class,()->submission.createSelfSourced(request(),1L));
        for(String table:List.of("zsjos_person","zsjos_lead","zsjos_lead_follow_up_record","zsjos_opportunity","zsjos_business_task","zsjos_business_event"))assertEquals(0,count(table),table);
        assertEquals(0,sent.get());
    }
    @Test void disabledRuleProducesNoPartialDataAndInternalEntryRequiresTransaction() {
        when(get(LeadFollowUpRuleService.class).requireEnabledRule()).thenThrow(new IllegalStateException("rule disabled"));
        assertThrows(RuntimeException.class,()->submission.createSelfSourced(request(),1L));assertEquals(0,count("zsjos_lead"));assertEquals(0,sent.get());
        get(LeadMapper.class).insert(new LeadDO().setId(1L).setOwnerUserId(1L));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->automation.complete(1L,1L,null));
    }
    @Test void linkedProviderAndEducationCreateDoNotAutomaticallyQualify() {
        var req=request();req.setNewMediaProviderUserId(2L);var result=submission.createSelfSourced(req,1L);assertFalse(result.getAutomaticQualificationApplied());assertEquals("pending",result.getQualificationStatus());assertEquals(0,count("zsjos_lead_follow_up_record"));assertEquals(0,count("zsjos_opportunity"));
        var education=request();education.setIdempotencyKey("education-test");var result2=submission.createEducationSelfSourced(education,1L);assertFalse(result2.getAutomaticQualificationApplied());assertEquals(0,count("zsjos_opportunity"));
    }

    @Test void laterHumanInvalidationKeepsAutomaticFactAndStartsUnmarkedRound() {
        var result=submission.createSelfSourced(request(),1L);
        var command=new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadJudgeInvalidReqVO();
        command.setIdempotencyKey("manual-invalid");command.setReasonCode("a");command.setDescription("后续人工核实无意向");
        get(LeadQualificationService.class).judgeInvalid(result.getLeadId(),1L,command);
        assertEquals(2,get(LeadMapper.class).selectById(result.getLeadId()).getQualificationRoundNo());
        var tasks=get(BusinessTaskMapper.class).selectList();
        var automatic=tasks.stream().filter(t->TASK_TYPE_QUALIFICATION.equals(t.getTaskType())&&LeadAutomaticGeneration.isAutomatic(t.getPayload())).findFirst().orElseThrow();
        var manual=tasks.stream().filter(t->TASK_TYPE_QUALIFICATION.equals(t.getTaskType())&&!LeadAutomaticGeneration.isAutomatic(t.getPayload())).findFirst().orElseThrow();
        assertEquals("completed",automatic.getStatus());assertEquals("cancelled",manual.getStatus());
        var invalid=get(BusinessEventMapper.class).selectByLeadId(result.getLeadId()).stream().filter(e->EVENT_LEAD_QUALIFIED_INVALID.equals(e.getEventType())).findFirst().orElseThrow();
        assertFalse(LeadAutomaticGeneration.isAutomatic(invalid.getRelatedObjectRefs()));
        get(LeadQualificationService.class).judgeInvalid(result.getLeadId(),1L,command);
        assertEquals(3,count("zsjos_business_task"));
    }

    @Test void dictionaryRenameDoesNotRewriteAutomaticHistory() {
        var result=submission.createSelfSourced(request(),1L);
        when(get(DictDataApi.class).getDictDataList(anyString())).thenReturn(List.of(dict("other","后来改名")));
        var record=followUp.getPage(result.getLeadId(),1,100).getList().getFirst();
        assertEquals("配置标签",record.getMethodLabel());assertEquals("配置标签",record.getResultLabel());
        assertEquals("录单分类",record.getCategoryAfterLabel());
    }

    @Test void provenanceFailureRollsBackCompleteChainAndCommitNotifications() {
        jdbc.execute("ALTER TABLE zsjos_business_task ADD CONSTRAINT reject_auto_payload CHECK (payload NOT LIKE '%sales_self_sourced_auto%')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->submission.createSelfSourced(request(),1L));
        for(String table:List.of("zsjos_person","zsjos_lead","zsjos_lead_follow_up_record","zsjos_opportunity","zsjos_business_task","zsjos_business_event"))assertEquals(0,count(table),table);
        assertEquals(0,sent.get());
    }

    @Test void concurrentSameRequestProducesOneChainAndRetryReturnsIt() throws Exception {
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var work=(java.util.concurrent.Callable<Boolean>)()->{
                login();
                try {barrier.await();submission.createSelfSourced(request(),1L);return true;}
                catch(org.springframework.dao.DuplicateKeyException conflict){return false;}
                finally {SecurityContextHolder.clearContext();TenantContextHolder.clear();}
            };
            var a=workers.submit(work);var b=workers.submit(work);
            assertTrue(a.get(20,java.util.concurrent.TimeUnit.SECONDS)|b.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        var result=submission.createSelfSourced(request(),1L);
        assertTrue(result.getAutomaticQualificationApplied());
        for(String table:List.of("zsjos_person","zsjos_lead","zsjos_lead_follow_up_record","zsjos_opportunity"))assertEquals(1,count(table),table);
        assertEquals(2,count("zsjos_business_task"));assertEquals(3,sent.get());
    }
}
