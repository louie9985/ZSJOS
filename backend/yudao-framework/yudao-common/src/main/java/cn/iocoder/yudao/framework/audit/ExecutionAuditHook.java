package cn.iocoder.yudao.framework.audit;

/** Optional integration point for business audit implementations. */
public interface ExecutionAuditHook {
    default void onStart(ExecutionAuditContext context) { }
    default void onSuccess(ExecutionAuditContext context, long durationMs) { }
    default void onFailure(ExecutionAuditContext context, long durationMs, Throwable error) { }
}
