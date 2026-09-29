package cn.iocoder.yudao.module.bpm.framework.flowable.core.behavior;

import cn.iocoder.yudao.framework.audit.ExecutionAuditContext;
import cn.iocoder.yudao.framework.audit.ExecutionAuditContextHolder;
import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import cn.iocoder.yudao.framework.audit.ExecutionAuditRunner;
import cn.iocoder.yudao.framework.audit.SelfAuditingRunnable;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.job.api.JobInfo;
import org.flowable.job.service.JobServiceConfiguration;
import org.flowable.job.service.impl.asyncexecutor.AbstractAsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.ExecuteAsyncRunnable;
import org.flowable.job.service.impl.nontx.NonTransactionalJobHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** Bridges persisted Flowable job identity into tenant-scoped business execution and audit. */
public class BpmAuditedAsyncRunnable extends ExecuteAsyncRunnable implements SelfAuditingRunnable {

    private final List<ExecutionAuditHook> hooks;
    private final ExecutionAuditContext auditContext;

    public BpmAuditedAsyncRunnable(JobInfo job, JobServiceConfiguration configuration,
                                   AbstractAsyncExecutor executor, List<ExecutionAuditHook> hooks) {
        super(job, configuration, configuration.getJobEntityManager(),
                executor.getAsyncRunnableExecutionExceptionHandler(), executor.getJobExecutionObservationProvider());
        this.hooks = hooks;
        this.auditContext = new ExecutionAuditContext(
                "ASYNC", "flowable.job." + job.getJobHandlerType(), null, null, null, null,
                job.getTenantId(), Map.of("jobId", job.getId()));
    }

    @Override
    public void run() {
        String tenant = job.getTenantId();
        // A tenantless job must not borrow an unrelated submitting thread's tenant.
        TenantUtils.execute(tenant == null || tenant.isBlank() ? null : Long.valueOf(tenant), () -> {
            try (ExecutionAuditContextHolder.Scope ignored = ExecutionAuditContextHolder.restore(auditContext)) {
                super.run();
            }
        });
    }

    @Override
    protected void handleTransactionalJob(boolean unlock) {
        audit(() -> {
            super.handleTransactionalJob(unlock);
            return null;
        });
    }

    @Override
    protected boolean handleNontransactionalJob(NonTransactionalJobHandler<Object> handler, boolean unlock) {
        return audit(() -> super.handleNontransactionalJob(handler, unlock));
    }

    private <T> T audit(Callable<T> action) {
        // Audit after the command commits/rolls back, before Flowable consumes the exception for retry.
        // Wrapping run() instead would incorrectly record those swallowed failures as success.
        try {
            return ExecutionAuditRunner.run(auditContext, hooks, action);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FlowableException("Flowable job execution failed", exception);
        }
    }
}
