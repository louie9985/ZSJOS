package cn.iocoder.yudao.framework.audit;

import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;

/** Runs message or background work with optional execution audit hooks. */
@Slf4j
public final class ExecutionAuditRunner {
    private ExecutionAuditRunner() {}

    public static <T> T run(ExecutionAuditContext context, List<ExecutionAuditHook> hooks,
                            Callable<T> action) throws Exception {
        long started = System.currentTimeMillis();
        if (hooks != null) hooks.forEach(h -> safeStart(h, context));
        try {
            T result = action.call();
            long elapsed = System.currentTimeMillis() - started;
            if (hooks != null) hooks.forEach(h -> safeSuccess(h, context, elapsed));
            return result;
        } catch (Throwable error) {
            long elapsed = System.currentTimeMillis() - started;
            if (hooks != null) hooks.forEach(h -> safeFailure(h, context, elapsed, error));
            if (error instanceof Exception e) throw e;
            if (error instanceof Error e) throw e;
            throw new RuntimeException(error);
        }
    }
    private static void safeStart(ExecutionAuditHook h, ExecutionAuditContext c) { try { h.onStart(c); } catch (Throwable e) { log.warn("execution audit hook start failed type={} name={}", c.executionType(), c.executionName(), e); } }
    private static void safeSuccess(ExecutionAuditHook h, ExecutionAuditContext c, long d) { try { h.onSuccess(c, d); } catch (Throwable e) { log.warn("execution audit hook success failed type={} name={}", c.executionType(), c.executionName(), e); } }
    private static void safeFailure(ExecutionAuditHook h, ExecutionAuditContext c, long d, Throwable e) { try { h.onFailure(c, d, e); } catch (Throwable hookError) { log.warn("execution audit hook failure callback failed type={} name={} cause={}", c.executionType(), c.executionName(), e.toString(), hookError); } }
}
