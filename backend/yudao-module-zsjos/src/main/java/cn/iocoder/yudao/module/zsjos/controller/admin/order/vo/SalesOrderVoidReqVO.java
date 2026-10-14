package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesOrderVoidReqVO {
    @NotNull private Integer orderVersion;
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Size(max = 128) private String idempotencyKey;
}
