package cn.iocoder.yudao.module.zsjos.controller.admin.impersonation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImpersonationSessionRespVO {
    private Long id;
    private Long administratorUserId;
    private String administratorNameSnapshot;
    private Long targetUserId;
    private String targetNameSnapshot;
    private String reason;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime endedAt;
    private String endedReason;
}
