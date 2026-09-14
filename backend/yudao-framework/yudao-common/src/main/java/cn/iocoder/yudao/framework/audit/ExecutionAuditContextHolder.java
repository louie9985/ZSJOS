package cn.iocoder.yudao.framework.audit;

/** Thread-bound execution audit context for propagation across async boundaries. */
public final class ExecutionAuditContextHolder {
    private static final ThreadLocal<ExecutionAuditContext> CONTEXT = new ThreadLocal<>();

    private ExecutionAuditContextHolder() {
    }

    public static ExecutionAuditContext get() {
        return CONTEXT.get();
    }

    public static void set(ExecutionAuditContext context) {
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
    }

    public static ExecutionAuditContext capture() {
        return CONTEXT.get();
    }

    public static Scope restore(ExecutionAuditContext context) {
        ExecutionAuditContext previous = CONTEXT.get();
        set(context);
        return () -> set(previous);
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
