package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.LeadOverturnValidReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real transaction, tenant interceptor and mappers; System/Infra/cashback/notification are doubles. */
class SupervisorLeadOverturnTransactionTest {
    JdbcTemplate jdbc;
    DataSourceTransactionManager tx;
    SupervisorLeadOverturnService service;
    LeadMapper leads;
    LeadAppealMapper appeals;
    CashbackService cashback;
    Map<Class<?>,Object> beans = new HashMap<>();
    boolean mysql;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9L);
        mysql=System.getenv("ZSJOS_OVERTURN_MYSQL_URL")!=null;
        var ds=mysql ? new DriverManagerDataSource(System.getenv("ZSJOS_OVERTURN_MYSQL_URL"),System.getenv("ZSJOS_OVERTURN_MYSQL_USER"),System.getenv("ZSJOS_OVERTURN_MYSQL_PASSWORD"))
                : new DriverManagerDataSource("jdbc:h2:mem:overturn"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        if(mysql){
            String database="ovtx_"+UUID.randomUUID().toString().replace("-", "");
            new JdbcTemplate(ds).execute("CREATE DATABASE "+database+" CHARACTER SET utf8mb4");
            ds.setUrl(System.getenv("ZSJOS_OVERTURN_MYSQL_URL").replace("/mysql?", "/"+database+"?"));
        }
        jdbc=new JdbcTemplate(ds);tx=new DataSourceTransactionManager(ds);
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("overturn-test",new SpringManagedTransactionFactory(),ds));
        var interceptor=new MybatisPlusInterceptor();interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler(){
            public LongValue getTenantId(){return new LongValue(TenantContextHolder.getRequiredTenantId());}
        }));config.addInterceptor(interceptor);if(!mysql)config.addInterceptor(new LeadSelfSourcedTransactionTest.H2Bits());
        var mappers=List.of(LeadMapper.class,LeadAppealMapper.class,OpportunityMapper.class,LeadIntendedProductMapper.class,BusinessEventMapper.class);
        mappers.forEach(config::addMapper);
        var template=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        for(var mapper:mappers)beans.put(mapper,template.getMapper(mapper));
        for(var type:List.of(LeadDO.class,LeadAppealDO.class,OpportunityDO.class,LeadIntendedProductDO.class,BusinessEventDO.class)) {
            var info=TableInfoHelper.getTableInfo(type);var ddl=new StringBuilder("CREATE TABLE "+info.getTableName()+" (id BIGINT AUTO_INCREMENT PRIMARY KEY");
            for(var field:info.getFieldList()){
                var t=field.getPropertyType();var sqlType=t==String.class?(field.getColumn().equals("idempotency_key")?"VARCHAR(128)":mysql?"LONGTEXT":"VARCHAR(16000)"):t==LocalDateTime.class?"TIMESTAMP NULL":t==java.math.BigDecimal.class?"DECIMAL(18,2)":"BIGINT";
                ddl.append(", ").append(field.getColumn()).append(" ").append(sqlType);
                if(field.getColumn().equals("deleted"))ddl.append(" DEFAULT 0");
            }jdbc.execute(ddl.append(mysql?") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4":")").toString());
        }
        jdbc.execute("CREATE UNIQUE INDEX event_key ON zsjos_business_event(tenant_id,idempotency_key)");
        jdbc.execute("CREATE UNIQUE INDEX opportunity_lead ON zsjos_opportunity(tenant_id,lead_id)");
        jdbc.update("INSERT INTO zsjos_lead(id,tenant_id,person_id,owner_user_id,status,assignment_status,qualified_at,invalid_reason,invalid_reason_label_snapshot,invalid_description,invalid_evidence_refs,lead_category_label_snapshot,version) VALUES(1,9,5,20,'invalid','owned',CURRENT_TIMESTAMP,'wrong','原无效','原说明','[]','历史分类',1)");
        jdbc.update("INSERT INTO zsjos_opportunity(id,tenant_id,lead_id,type,status,lost_reason,next_follow_up_at) VALUES(2,9,1,'initial_conversion','lost','原无效',CURRENT_TIMESTAMP)");
        leads=(LeadMapper)beans.get(LeadMapper.class);appeals=(LeadAppealMapper)beans.get(LeadAppealMapper.class);
        var raw=new SupervisorLeadOverturnService();
        for(var field:raw.getClass().getDeclaredFields())if(field.isAnnotationPresent(Resource.class))
            ReflectionTestUtils.setField(raw,field.getName(),beans.computeIfAbsent(field.getType(), key->mock(key)));
        when(((SecurityFrameworkService)beans.get(SecurityFrameworkService.class)).hasPermission(SupervisorLeadOverturnPolicy.PERMISSION)).thenReturn(true);
        when(((LeadObjectPermissionService)beans.get(LeadObjectPermissionService.class)).getManagedUserIds(30L)).thenReturn(Set.of(20L));
        cashback=(CashbackService)beans.get(CashbackService.class);
        var factory=new ProxyFactory(raw);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));service=(SupervisorLeadOverturnService)factory.getProxy();
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();if(!mysql)jdbc.execute("SHUTDOWN");}
    LeadOverturnValidReqVO request(){var r=new LeadOverturnValidReqVO();r.setReason("已核实");r.setIdempotencyKey("test");r.setQualificationToken(SupervisorLeadOverturnPolicy.token(leads.selectById(1L),null));return r;}
    @Test void persistsNullClearsAndOnlyOneEventOnReplay(){
        var r=request();service.overturn(1L,30L,r);service.overturn(1L,30L,r);
        var row=leads.selectById(1L);assertEquals("valid",row.getStatus());assertNull(row.getInvalidReason());assertNull(row.getInvalidDescription());assertNull(row.getInvalidEvidenceRefs());
        assertEquals("历史分类",row.getLeadCategoryLabelSnapshot());assertEquals(20L,row.getOwnerUserId());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_event",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_opportunity WHERE lost_reason IS NOT NULL OR next_follow_up_at IS NOT NULL",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_lead_appeal",Integer.class));verify(cashback,times(1)).ensureValidCashback(1L);
    }
    @Test void failureRollsBackLeadOpportunityAndEvent(){
        doThrow(new IllegalStateException("cashback failure")).when(cashback).ensureValidCashback(1L);
        assertThrows(IllegalStateException.class,()->service.overturn(1L,30L,request()));
        assertEquals("invalid",leads.selectById(1L).getStatus());assertEquals("原说明",leads.selectById(1L).getInvalidDescription());
        assertEquals("lost",jdbc.queryForObject("SELECT status FROM zsjos_opportunity WHERE id=2",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_event",Integer.class));
        verifyNoInteractions(beans.get(LeadNotifyEventPublisher.class));
    }
    @Test void tenantInterceptorRejectsForeignLead(){
        var r=request();TenantContextHolder.setTenantId(10L);assertNull(leads.selectById(1L));assertThrows(RuntimeException.class,()->service.overturn(1L,30L,r));
        assertEquals("invalid",jdbc.queryForObject("SELECT status FROM zsjos_lead WHERE id=1",String.class));
    }
    @Test void concurrentRetriesCommitExactlyOnce() throws Exception {
        var r=request();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Void> call=()->{TenantContextHolder.setTenantId(9L);try{start.await();service.overturn(1L,30L,r);return null;}finally{TenantContextHolder.clear();}};
        try{var a=pool.submit(call);var b=pool.submit(call);start.countDown();a.get(8,TimeUnit.SECONDS);b.get(8,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_event",Integer.class));verify(cashback,times(1)).ensureValidCashback(1L);
    }
    @Test void appealCommittedWhileWaitingForLeadLockBlocksOverturn() throws Exception {
        var r=request();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var appeal=pool.submit(()->{TenantContextHolder.setTenantId(9L);try{new TransactionTemplate(tx).executeWithoutResult(status->{
                leads.selectByIdForUpdate(1L,9L);locked.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
                var row=new LeadAppealDO();row.setLeadId(1L);row.setTenantId(9L);row.setRoundNo(1);row.setReviewStage("sales_manager");row.setStatus("sales_manager_reviewing");appeals.insert(row);
            });}finally{TenantContextHolder.clear();}});
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            var overturn=pool.submit(()->{TenantContextHolder.setTenantId(9L);try{assertThrows(RuntimeException.class,()->service.overturn(1L,30L,r));}finally{TenantContextHolder.clear();}});
            release.countDown();appeal.get(8,TimeUnit.SECONDS);overturn.get(8,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertEquals("invalid",leads.selectById(1L).getStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_event",Integer.class));
    }
}
