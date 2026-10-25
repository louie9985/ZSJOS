package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.mybatis.spring.SqlSessionTemplate;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL 8, tenant parser and nested Spring transactions; separate fixture database only. */
@EnabledIfEnvironmentVariable(named="ACCOUNT_DELETE_TEST_DB", matches="account_delete_verify_[a-zA-Z0-9_]+")
class MediaAccountDeleteMySqlTest {
    static JdbcTemplate jdbc;
    static MediaAccountMapper accounts;
    static MediaAccountDeleteRequestMapper requests;
    static ProductionTicketMapper tickets;
    static ContentReviewBatchMapper reviews;
    static BusinessTaskMapper tasks;
    static DataSourceTransactionManager manager;
    static AtomicLong ids = new AtomicLong(10000);
    long id;

    @BeforeAll static void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + System.getenv("ACCOUNT_DELETE_TEST_DB")
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
                "root", System.getenv("ACCOUNT_DELETE_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (var mapper : java.util.List.of(MediaAccountMapper.class, MediaAccountDeleteRequestMapper.class,
                ProductionTicketMapper.class, ContentReviewBatchMapper.class, BusinessTaskMapper.class)) config.addMapper(mapper);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config); factory.setPlugins(plugin);
        var session = new SqlSessionTemplate(factory.getObject());
        accounts = session.getMapper(MediaAccountMapper.class); requests = session.getMapper(MediaAccountDeleteRequestMapper.class);
        tickets = session.getMapper(ProductionTicketMapper.class); reviews = session.getMapper(ContentReviewBatchMapper.class);
        tasks = session.getMapper(BusinessTaskMapper.class);
    }

    @BeforeEach void before() { TenantContextHolder.setTenantId(1L); id = ids.incrementAndGet(); }
    @AfterEach void after() { TenantContextHolder.clear(); }

    void ticket(long row, String array, String status, long tenant) {
        jdbc.update("INSERT INTO zsjos_production_ticket(id,ticket_no,account_id,account_ids_json,owner_operator_user_id,reviewer_user_id,status,tenant_id,version) VALUES(?,?,?,?,1,2,?,?,0)", row,"TEST-"+row,id,array,status,tenant);
    }
    void review(long row, String array, String status, long tenant) {
        jdbc.update("INSERT INTO zsjos_content_review_batch(id,batch_no,account_id,account_ids_json,operator_user_id,status,current_stage,tenant_id,version) VALUES(?,?,?,?,1,?,'director',?,0)",row,"TEST-"+row,id,array,status,tenant);
    }

    @Test void cleanupExecutesThroughTenantParserAndPreservesOtherAccountsAndHistory() {
        long base = id * 10;
        ticket(base,"["+id+",999]","pending",1); ticket(base+1,"["+id+"]","pending",1);
        ticket(base+2,"["+id+"]","completed",1); ticket(base+3,"["+id+"]","pending",2);
        ticket(base+4,"[\""+id+"\"]","pending",1);
        review(base,"["+id+",999]","DIRECTOR_REVIEW",1); review(base+1,"["+id+"]","FINAL_REVIEW",1);
        review(base+2,"["+id+"]","APPROVED",1); review(base+3,"["+id+"]","DRAFT",2);
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            assertEquals(2,tickets.excludeDeletedAccount(id)); assertEquals(2,reviews.excludeDeletedAccount(id));
        });
        assertEquals("[999]",jdbc.queryForObject("SELECT account_ids_json FROM zsjos_production_ticket WHERE id=?",String.class,base));
        assertEquals("cancelled",jdbc.queryForObject("SELECT status FROM zsjos_production_ticket WHERE id=?",String.class,base+1));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM zsjos_production_ticket WHERE id=?",Integer.class,base+2));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM zsjos_production_ticket WHERE id=?",Integer.class,base+3));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM zsjos_production_ticket WHERE id=?",Integer.class,base+4));
        assertEquals("[]",jdbc.queryForObject("SELECT account_ids_json FROM zsjos_content_review_batch WHERE id=?",String.class,base+1));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM zsjos_content_review_batch WHERE id=?",Integer.class,base+2));
        assertEquals(0,jdbc.queryForObject("SELECT version FROM zsjos_content_review_batch WHERE id=?",Integer.class,base+3));
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            assertEquals(0,tickets.excludeDeletedAccount(id)); assertEquals(0,reviews.excludeDeletedAccount(id));
        });
    }

    @Test void approvalFailureRollsBackAllCleanupButCommitsBoundedRetryStateThenRetrySucceeds() {
        String process = "test-delete-"+id;
        jdbc.update("INSERT INTO zsjos_media_account(id,account_no,ownership_type,tenant_id,run_status,delete_status,delete_process_instance_id,delete_reviewer_user_id,version) VALUES(?,?,'student',1,'delete_pending','pending',?,2,0)",id,"TEST-"+id,process);
        jdbc.update("INSERT INTO zsjos_media_account_delete_request(account_id,process_instance_id,account_snapshot_json,reason,requested_by_user_id,reviewer_user_id,status,tenant_id,version) VALUES(?,?,'{}','误新增',1,2,'pending',1,0)",id,process);
        ticket(id,"["+id+"]","pending",1);
        jdbc.update("INSERT INTO zsjos_business_task(id,task_type,biz_type,biz_id,status,assignee_type,assignee_id,tenant_id,version) VALUES(?,'diagnosis','media_account_diagnosis',?,'pending','ADMIN',2,1,0)",id,id);
        var failingReviews = mock(ContentReviewBatchMapper.class);
        when(failingReviews.excludeDeletedAccount(id)).thenThrow(new IllegalStateException("sensitive-sql-".repeat(300)));
        var target = new MediaAccountDeleteService();
        ReflectionTestUtils.setField(target,"mapper",accounts); ReflectionTestUtils.setField(target,"requestMapper",requests);
        ReflectionTestUtils.setField(target,"tickets",tickets); ReflectionTestUtils.setField(target,"taskMapper",tasks);
        ReflectionTestUtils.setField(target,"reviewBatches",failingReviews); ReflectionTestUtils.setField(target,"transactionManager",manager);
        ReflectionTestUtils.setField(target,"events",mock(MediaWorkflowEventService.class));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var service = (MediaAccountDeleteService) proxy.getProxy();
        var event = new BpmProcessInstanceStatusEvent(this); event.setId(process); event.setStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus()); event.setReason("同意删除");
        assertDoesNotThrow(() -> service.onResult(event));
        assertEquals("pending",jdbc.queryForObject("SELECT status FROM zsjos_production_ticket WHERE id=?",String.class,id));
        assertEquals("pending",jdbc.queryForObject("SELECT status FROM zsjos_business_task WHERE id=?",String.class,id));
        assertEquals("delete_pending",jdbc.queryForObject("SELECT run_status FROM zsjos_media_account WHERE id=?",String.class,id));
        var request = requests.byProcess(process);
        assertEquals("approved_pending",request.getStatus()); assertTrue(request.getLastError().length()<1000);
        assertFalse(request.getLastError().contains("sensitive-sql"));
        ReflectionTestUtils.setField(target,"reviewBatches",reviews);
        service.retryByAccount(id);
        assertEquals("deleted",jdbc.queryForObject("SELECT run_status FROM zsjos_media_account WHERE id=?",String.class,id));
        assertEquals("cancelled",jdbc.queryForObject("SELECT status FROM zsjos_production_ticket WHERE id=?",String.class,id));
        assertEquals("cancelled",jdbc.queryForObject("SELECT status FROM zsjos_business_task WHERE id=?",String.class,id));
        assertEquals("completed",requests.byProcess(process).getStatus());
        assertDoesNotThrow(() -> service.onResult(event));
    }
}
