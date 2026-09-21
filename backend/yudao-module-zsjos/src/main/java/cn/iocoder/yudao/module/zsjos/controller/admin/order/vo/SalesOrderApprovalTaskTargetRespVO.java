package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.Data;

@Data
public class SalesOrderApprovalTaskTargetRespVO {
    private String workType;
    private Long orderId;
    private String taskId;
    private Boolean approvalReasonRequired;
    private String taskDefinitionKey;
    private String center;
    private Long confirmationId;
    private String status;
}
