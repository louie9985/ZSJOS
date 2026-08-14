package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderSupervisorDecisionReqVO {
    @NotNull private Long confirmationId;
    @NotBlank private String taskId;
    @NotNull private Long approvalRoundId;
    @NotNull private Integer orderVersion;
    @NotNull private Integer roundVersion;
    @NotNull private Integer confirmationVersion;
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
