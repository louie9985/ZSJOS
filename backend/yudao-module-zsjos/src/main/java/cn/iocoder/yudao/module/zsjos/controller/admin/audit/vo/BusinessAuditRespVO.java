package cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BusinessAuditRespVO {
    private Long id;
    private Long operatorUserId;
    private String operatorNameSnapshot;
    private String operatorRoleSnapshot;
    private String categoryCode;
    private String actionCode;
    private String targetType;
    private String targetId;
    private String detailJson;
    private String sourceIp;
    private String sourceType;
    private String traceId;
    private String requestMethod;
    private String requestPath;
    private String resultStatus;
    private Integer resultCode;
    private String resultMessage;
    private LocalDateTime occurredAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
}
