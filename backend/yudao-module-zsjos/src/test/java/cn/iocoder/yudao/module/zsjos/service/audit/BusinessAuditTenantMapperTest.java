package cn.iocoder.yudao.module.zsjos.service.audit;

import cn.iocoder.yudao.framework.audit.ExecutionAuditContext;
import cn.iocoder.yudao.framework.audit.ExecutionAuditRunner;
import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import cn.iocoder.yudao.framework.audit.ExecutionAuditContextHolder;
import cn.iocoder.yudao.framework.quartz.config.YudaoAsyncAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosExecutionAuditHook;
import cn.iocoder.yudao.module.zsjos.dal.mysql.audit.BusinessAuditLogMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessAuditTenantMapperTest {
    @Test void realAuditInsertRequiresTenantAndRestoresContextEvenOnDatabaseFailure() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:audit-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var keepAlive = source.getConnection()) {
            var jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                CREATE TABLE zsjos_business_audit_log (
                  id bigint primary key, tenant_id bigint not null, operator_user_id bigint,
                  operator_name_snapshot varchar(100), operator_role_snapshot varchar(100),
                  category_code varchar(100), action_code varchar(100), target_type varchar(100), target_id varchar(200),
                  detail_json varchar(8000), source_type varchar(100), result_status varchar(100), result_code int,
                  occurred_at timestamp, finished_at timestamp, executor_type varchar(100), executor_identity varchar(200),
                  duration_ms bigint, create_time timestamp, update_time timestamp, creator varchar(64), updater varchar(64),
                  deleted boolean default false)
                """);
            var config = new MybatisConfiguration();
            config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
            var interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
            config.addInterceptor(interceptor);
            config.addMapper(BusinessAuditLogMapper.class);
            var factory = new MybatisSqlSessionFactoryBuilder().build(config);
            try (var session = factory.openSession(true)) {
                var service = new BusinessAuditServiceImpl();
                ReflectionTestUtils.setField(service, "mapper", session.getMapper(BusinessAuditLogMapper.class));
                var hook = new ZsjosExecutionAuditHook(service, mock(TenantFrameworkService.class));
                var context = new ExecutionAuditContext("ASYNC", "flowable.job.timer", null, null, null, null, "1", Map.of());
                TenantContextHolder.setTenantId(99L);
                TenantContextHolder.setIgnore(true);
                hook.onSuccess(context, 5);
                assertEquals(1L, jdbc.queryForObject("SELECT tenant_id FROM zsjos_business_audit_log", Long.class));
                assertEquals("SUCCESS", jdbc.queryForObject("SELECT result_status FROM zsjos_business_audit_log", String.class));
                assertEquals(99L, TenantContextHolder.getTenantId());
                assertTrue(TenantContextHolder.isIgnore());
                TenantContextHolder.clear();
                assertThrows(Exception.class, () -> hook.onSuccess(new ExecutionAuditContext("ASYNC", "missing", null, null, null, null, null, Map.of()), 1));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_audit_log", Integer.class));
                jdbc.execute("ALTER TABLE zsjos_business_audit_log ADD CONSTRAINT reject_new CHECK (target_id = 'flowable.job.timer')");
                assertEquals("business-result", ExecutionAuditRunner.run(new ExecutionAuditContext("ASYNC", "database-rejected", null, null, null, null, "1", Map.of()), List.of(hook), () -> "business-result"));
                assertNull(TenantContextHolder.getTenantId());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_audit_log", Integer.class));
                jdbc.execute("ALTER TABLE zsjos_business_audit_log DROP CONSTRAINT reject_new");
                var tenants = mock(TenantFrameworkService.class);
                when(tenants.getTenantIds()).thenReturn(List.of(1L));
                var completed = new CountDownLatch(1);
                var auditFailure = new AtomicReference<Throwable>();
                try (var spring = new AnnotationConfigApplicationContext()) {
                    spring.registerBean("auditHook", ExecutionAuditHook.class, () -> new ExecutionAuditHook() {
                        @Override public void onSuccess(ExecutionAuditContext captured, long duration) {
                            try (var workerSession = factory.openSession(true)) {
                                assertEquals("java.util.concurrent.FutureTask", captured.executionName());
                                assertNull(captured.tenantId());
                                var workerService = new BusinessAuditServiceImpl();
                                ReflectionTestUtils.setField(workerService, "mapper", workerSession.getMapper(BusinessAuditLogMapper.class));
                                new ZsjosExecutionAuditHook(workerService, tenants).onSuccess(captured, duration);
                                assertNull(TenantContextHolder.getTenantId());
                                assertFalse(TenantContextHolder.isIgnore());
                            } catch (Throwable error) {
                                auditFailure.set(error);
                            } finally {
                                completed.countDown();
                            }
                        }
                    });
                    spring.registerBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class, () -> {
                        var executor = new ThreadPoolTaskExecutor();
                        executor.setCorePoolSize(1);
                        executor.setThreadNamePrefix("async-audit-test-");
                        return executor;
                    });
                    spring.register(YudaoAsyncAutoConfiguration.class);
                    spring.refresh();
                    ExecutionAuditContextHolder.set(null);
                    assertEquals("processed", spring.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class)
                            .submit(() -> {
                                assertNull(TenantContextHolder.getTenantId());
                                return "processed";
                            }).get(5, TimeUnit.SECONDS));
                    assertTrue(completed.await(5, TimeUnit.SECONDS));
                    assertNull(auditFailure.get(), () -> String.valueOf(auditFailure.get()));
                }
                verify(tenants).validTenant(1L);
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_business_audit_log", Integer.class));
                assertEquals(1L, jdbc.queryForObject("SELECT tenant_id FROM zsjos_business_audit_log WHERE executor_identity = 'java.util.concurrent.FutureTask'", Long.class));
                assertEquals("SUCCESS", jdbc.queryForObject("SELECT result_status FROM zsjos_business_audit_log WHERE executor_identity = 'java.util.concurrent.FutureTask'", String.class));
            }
        } finally {
            TenantContextHolder.clear();
            ExecutionAuditContextHolder.set(null);
        }
    }
}
