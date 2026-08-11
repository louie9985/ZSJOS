package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderDecisionReqVO {
    @NotBlank private String taskId;
    @NotBlank @Size(max = 1000) private String reason;
}
