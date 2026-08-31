package cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ImpersonationAuditRespVO {
    private Long id;
    private Long sessionId;
    private Long administratorUserId;
    private Long targetUserId;
    private String httpMethod;
    private String requestPath;
    private LocalDateTime occurredAt;
}
