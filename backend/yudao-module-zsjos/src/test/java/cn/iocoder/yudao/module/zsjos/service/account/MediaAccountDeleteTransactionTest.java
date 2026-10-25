package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaAccountDeleteTransactionTest {
    // Only MySQL bit literals are adapted; production mapper SQL and Spring transactions run unchanged.
    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(
            type=org.apache.ibatis.executor.statement.StatementHandler.class, method="prepare",
            args={java.sql.Connection.class, Integer.class}))
    public static class H2Bits implements org.apache.ibatis.plugin.Interceptor {
        @Override public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            var sql = ((org.apache.ibatis.executor.statement.StatementHandler) invocation.getTarget()).getBoundSql();
            org.apache.ibatis.reflection.SystemMetaObject.forObject(sql).setValue("sql", sql.getSql().replaceAll("(?i)b'([01])'", "$1"));
            return invocation.proceed();
        }
    }
    @Test void directDeletionCommitsStateAndRetainsRowsWithTenantIsolation() { verifyTransaction(false); }
    @Test void linkedCleanupFailureRollsBackAccountAndEarlierCleanup() { verifyTransaction(true); }

    private void verifyTransaction(boolean fail) {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:delete_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE zsjos_media_account (id BIGINT PRIMARY KEY, tenant_id BIGINT, deleted BOOLEAN DEFAULT FALSE, "
                + "version INT, director_user_id BIGINT, run_status VARCHAR(32), delete_status VARCHAR(32), "
                + "delete_process_instance_id VARCHAR(64), delete_requested_by_user_id BIGINT, delete_reviewer_user_id BIGINT, "
                + "delete_reason VARCHAR(500), delete_result_reason VARCHAR(500))");
        jdbc.execute("CREATE TABLE deletion_cleanup (account_id BIGINT)");
        jdbc.update("INSERT INTO zsjos_media_account(id,tenant_id,version,director_user_id,run_status) VALUES(1,1,3,20,'active'),(2,2,3,20,'active')");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), ds));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                new TenantDatabaseInterceptor(new TenantProperties())));
        config.addInterceptor(interceptor);
        config.addInterceptor(new H2Bits());
        config.addMapper(MediaAccountMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(config);
        try {
            var session = new SqlSessionTemplate(factory);
            var mapper = session.getMapper(MediaAccountMapper.class);
            var tasks = mock(BusinessTaskMapper.class);
            var tickets = mock(ProductionTicketMapper.class);
            var batches = mock(ContentReviewBatchMapper.class);
            var events = mock(MediaWorkflowEventService.class);
            when(tickets.excludeDeletedAccount(1L)).thenAnswer(invocation -> {
                jdbc.update("INSERT INTO deletion_cleanup(account_id) VALUES(1)");
                if (fail) throw new IllegalStateException("cleanup failed");
                return 1;
            });
            var target = new MediaAccountDeleteService();
            ReflectionTestUtils.setField(target, "mapper", mapper);
            ReflectionTestUtils.setField(target, "taskMapper", tasks);
            ReflectionTestUtils.setField(target, "tickets", tickets);
            ReflectionTestUtils.setField(target, "reviewBatches", batches);
            ReflectionTestUtils.setField(target, "events", events);
            var proxy = new ProxyFactory(target);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
            var service = (MediaAccountDeleteService) proxy.getProxy();
            TenantContextHolder.setTenantId(1L);
            if (fail) assertThrows(IllegalStateException.class, () -> service.deleteDirectly(1L, "误新增", 20L));
            else {
                service.deleteDirectly(1L, "误新增", 20L);
                assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                        () -> service.deleteDirectly(1L, "重复", 20L));
                assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                        () -> service.deleteDirectly(2L, "越界", 20L));
            }
            assertEquals(fail ? "active" : "deleted", jdbc.queryForObject("SELECT run_status FROM zsjos_media_account WHERE id=1", String.class));
            assertEquals(fail ? 3 : 4, jdbc.queryForObject("SELECT version FROM zsjos_media_account WHERE id=1", Integer.class));
            assertEquals(fail ? 0 : 1, jdbc.queryForObject("SELECT COUNT(*) FROM deletion_cleanup", Integer.class));
            assertEquals("active", jdbc.queryForObject("SELECT run_status FROM zsjos_media_account WHERE id=2", String.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_media_account", Integer.class));
            if (!fail) {
                assertEquals("误新增", jdbc.queryForObject("SELECT delete_reason FROM zsjos_media_account WHERE id=1", String.class));
                assertNull(jdbc.queryForObject("SELECT delete_process_instance_id FROM zsjos_media_account WHERE id=1", String.class));
            }
        } finally { TenantContextHolder.clear(); }
    }
}
