package cn.iocoder.yudao.framework.audit;

/**
 * A runtime adapter that audits the actual execution and owns its tenant scope.
 * Generic executor decorators must not add an outer success record: the adapter
 * may handle failures internally to preserve its runtime's retry semantics.
 */
public interface SelfAuditingRunnable extends Runnable {
}
