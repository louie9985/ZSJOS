package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderDecisionReqVO {
    @NotBlank private String taskId;
    @jakarta.validation.constraints.NotNull private Long approvalRoundId;
    @jakarta.validation.constraints.NotNull private Integer orderVersion;
    @jakarta.validation.constraints.NotNull private Integer roundVersion;
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
