package cn.iocoder.yudao.module.zsjos.framework.audit;

import cn.iocoder.yudao.framework.audit.ExecutionAuditContext;
import cn.iocoder.yudao.framework.audit.ExecutionAuditHook;
import cn.iocoder.yudao.module.zsjos.service.audit.AuditActionCatalog;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

/** Persists non-HTTP execution events in the existing business audit stream. */
@Component
@RequiredArgsConstructor
public class ZsjosExecutionAuditHook implements ExecutionAuditHook {
    private final BusinessAuditService auditService;

    @Override
    public void onSuccess(ExecutionAuditContext context, long durationMs) {
        record(context, "SUCCESS", durationMs, null);
    }

    @Override
    public void onFailure(ExecutionAuditContext context, long durationMs, Throwable error) {
        record(context, "FAILURE", durationMs, error == null ? null : error.getClass().getSimpleName());
    }

    private void record(ExecutionAuditContext c, String status, long duration, String error) {
        String action = switch (c.executionType()) {
            case "ASYNC" -> AuditActionCatalog.EXECUTION_ASYNC;
            case "SYSTEM_REDIS_PUBSUB" -> AuditActionCatalog.EXECUTION_REDIS_PUBSUB;
            case "SYSTEM_REDIS_STREAM" -> AuditActionCatalog.EXECUTION_REDIS_STREAM;
            default -> AuditActionCatalog.EXECUTION_QUARTZ;
        };
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(c.metadata());
        details.put("executorType", c.executionType());
        details.put("executorIdentity", c.executionName());
        details.put("traceId", c.traceId());
        details.put("parentAuditId", c.parentAuditId());
        details.put("initiatorUserId", c.initiatorUserId());
        details.put("initiatorNameSnapshot", c.initiatorName());
        details.put("resultStatus", status);
        details.put("durationMs", duration);
        if (error != null) details.put("errorType", error);
        auditService.record(AuditActionCatalog.CATEGORY_EXECUTION, action, "execution", c.executionName(), "SYSTEM", details);
    }
}
