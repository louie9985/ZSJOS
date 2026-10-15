package cn.iocoder.yudao.module.bpm.framework.flowable.config;

import cn.iocoder.yudao.framework.audit.*;
import cn.iocoder.yudao.framework.quartz.config.YudaoAsyncAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AsyncExecutorAliasTest {
    static class Worker {
        @Async public CompletableFuture<String> run() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName() + ":" + TenantContextHolder.getTenantId());
        }
    }
    private ThreadPoolTaskExecutor pool(String prefix) {
        var pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setThreadNamePrefix(prefix);
        return pool;
    }
    @Test void defaultAsyncUsesAliasAndPreservesTtlAndAudit() throws Exception {
        verifyRouting(false);
    }
    @Test void explicitDefaultIsNotOverridden() throws Exception {
        verifyRouting(true);
    }
    private void verifyRouting(boolean explicit) throws Exception {
        var completed = new CountDownLatch(1);
        var audits = new CopyOnWriteArrayList<String>();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("audit", ExecutionAuditHook.class, () -> new ExecutionAuditHook() {
                public void onSuccess(ExecutionAuditContext c, long duration) {
                    audits.add(c.tenantId() + ":" + TenantContextHolder.getTenantId());
                    completed.countDown();
                }
            });
            context.registerBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class, () -> pool("application-test-"));
            context.registerBean("taskScheduler", ThreadPoolTaskScheduler.class, ThreadPoolTaskScheduler::new);
            if (explicit) context.registerBean("taskExecutor", ThreadPoolTaskExecutor.class, () -> pool("explicit-test-"));
            context.registerBean(Worker.class);
            context.register(YudaoAsyncAutoConfiguration.class);
            context.refresh();
            if (explicit) assertNotSame(context.getBean("applicationTaskExecutor"), context.getBean("taskExecutor"));
            else assertSame(context.getBean("applicationTaskExecutor"), context.getBean("taskExecutor"));
            TenantContextHolder.setTenantId(1L);
            ExecutionAuditContextHolder.set(new ExecutionAuditContext("TEST", "parent", null, null, null, null, "1", null));
            String result = context.getBean(Worker.class).run().get(5, TimeUnit.SECONDS);
            assertTrue(result.startsWith(explicit ? "explicit-test-" : "application-test-"));
            assertTrue(result.endsWith(":1"));
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertEquals(java.util.List.of("1:1"), audits);
        } finally {
            TenantContextHolder.clear();
            ExecutionAuditContextHolder.set(null);
        }
    }
}
