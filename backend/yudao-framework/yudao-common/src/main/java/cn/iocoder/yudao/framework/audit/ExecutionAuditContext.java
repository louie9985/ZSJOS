package cn.iocoder.yudao.framework.audit;

import java.util.Collections;
import java.util.Map;

/** Immutable metadata for non HTTP executions. */
public record ExecutionAuditContext(String executionType, String executionName,
                                    String traceId, String parentAuditId,
                                    Long initiatorUserId, String initiatorName,
                                    String tenantId, Map<String, Object> metadata) {
    public ExecutionAuditContext {
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }
}
