package cn.iocoder.yudao.framework.quartz.config;

import cn.iocoder.yudao.framework.audit.ExecutionAuditContext;
import cn.iocoder.yudao.framework.audit.ExecutionAuditContextHolder;
import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import cn.iocoder.yudao.framework.audit.ExecutionAuditRunner;
import org.springframework.core.task.TaskDecorator;
import java.util.List;

final class AuditTaskDecorator implements TaskDecorator {
    private final List<ExecutionAuditHook> hooks;
    AuditTaskDecorator(List<ExecutionAuditHook> hooks) { this.hooks = hooks; }
    @Override public Runnable decorate(Runnable task) {
        ExecutionAuditContext parent = ExecutionAuditContextHolder.capture();
        return () -> {
            ExecutionAuditContext c = parent == null ? new ExecutionAuditContext("ASYNC", task.getClass().getName(), null, null, null, null, null, null)
                    : new ExecutionAuditContext("ASYNC", task.getClass().getName(), parent.traceId(), parent.parentAuditId(), parent.initiatorUserId(), parent.initiatorName(), parent.tenantId(), parent.metadata());
            try (ExecutionAuditContextHolder.Scope scope = ExecutionAuditContextHolder.restore(c)) {
                try {
                    ExecutionAuditRunner.run(c, hooks, () -> { task.run(); return null; });
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }
        };
    }
}
