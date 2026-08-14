package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SalesOrderSupervisorConfirmationRespVO {
    private Long id;
    private Long orderId;
    private String orderNo;
    private String studentName;
    private Long approvalRoundId;
    private String taskDefinitionKey;
    private String taskId;
    private Long requesterUserId;
    private String requesterUserName;
    private Long supervisorUserId;
    private String requestReason;
    private String decisionReason;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private Integer version;
    private Integer orderVersion;
    private Integer roundVersion;
}
