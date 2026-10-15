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
    private final Long tenantId;

    public BpmAuditedAsyncRunnable(JobInfo job, JobServiceConfiguration configuration,
                                   AbstractAsyncExecutor executor, List<ExecutionAuditHook> hooks) {
        super(job, configuration, configuration.getJobEntityManager(),
                executor.getAsyncRunnableExecutionExceptionHandler(), executor.getJobExecutionObservationProvider());
        this.hooks = hooks;
        this.tenantId = parseTenant(job.getTenantId());
        this.auditContext = new ExecutionAuditContext(
                "ASYNC", "flowable.job." + job.getJobHandlerType(), null, null, null, null,
                tenantId == null ? null : tenantId.toString(), Map.of("jobId", job.getId()));
    }

    @Override
    public void run() {
        // A tenantless job must not borrow an unrelated submitting thread's tenant.
        // Validation belongs inside executeJob's failure boundary so Flowable can persist retries.
        TenantUtils.execute(tenantId, () -> {
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
            return ExecutionAuditRunner.run(auditContext, hooks, () -> {
                if (tenantId == null) {
                    throw new FlowableException("Flowable job requires a positive numeric tenant; jobId=" + job.getId());
                }
                return action.call();
            });
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FlowableException("Flowable job execution failed", exception);
        }
    }

    private static Long parseTenant(String value) {
        if (value == null || !value.matches("[0-9]+")) return null;
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
