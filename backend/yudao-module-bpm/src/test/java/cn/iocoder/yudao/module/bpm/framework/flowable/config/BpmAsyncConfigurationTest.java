package cn.iocoder.yudao.module.bpm.framework.flowable.config;

import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmActivityBehaviorFactory;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior.BpmAuditedAsyncRunnable;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.AbstractAsyncExecutor;
import org.flowable.job.api.JobInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmAsyncConfigurationTest {
    private void configure(SpringProcessEngineConfiguration config) {
        var beans = new DefaultListableBeanFactory();
        new BpmFlowableConfiguration().bpmProcessEngineConfigurationConfigurer(
            beans.getBeanProvider(org.flowable.common.engine.api.delegate.event.FlowableEventListener.class),
            beans.getBeanProvider(org.flowable.common.engine.api.delegate.FlowableFunctionDelegate.class),
            new BpmActivityBehaviorFactory(), beans.getBeanProvider(ExecutionAuditHook.class)).configure(config);
    }

    @Test void engineCreatedExecutorStillReceivesFactory() {
        var config = new SpringProcessEngineConfiguration();
        configure(config);
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
            "jdbc:h2:mem:engine-created-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        config.setDataSource(source);
        config.setTransactionManager(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        config.setDatabaseSchemaUpdate("create-drop");
        config.setAsyncExecutorActivate(false);
        var engine = config.buildProcessEngine();
        try {
            var executor = (AbstractAsyncExecutor) config.getAsyncExecutor();
            assertSame(config.getAsyncExecutorExecuteAsyncRunnableFactory(), executor.getExecuteAsyncRunnableFactory());
            var job = mock(JobInfo.class);
            when(job.getId()).thenReturn("test-job");
            when(job.getTenantId()).thenReturn("1");
            assertInstanceOf(BpmAuditedAsyncRunnable.class, executor.getExecuteAsyncRunnableFactory()
                .createExecuteAsyncRunnable(job, config.getJobServiceConfiguration()));
        } finally { engine.close(); }
    }

    @Test void unsupportedPreconfiguredExecutorFailsBeforeStartup() {
        var config = new SpringProcessEngineConfiguration();
        config.setAsyncExecutor(mock(AsyncExecutor.class));
        assertTrue(assertThrows(IllegalStateException.class, () -> configure(config))
            .getMessage().contains("AbstractAsyncExecutor"));
    }
}
