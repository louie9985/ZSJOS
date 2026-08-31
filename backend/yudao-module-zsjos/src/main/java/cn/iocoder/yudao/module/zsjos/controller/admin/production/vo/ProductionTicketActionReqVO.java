package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductionTicketActionReqVO {
    @NotNull private Integer version;
    @NotBlank @Size(max = 64) private String idempotencyKey;
    @Size(max = 500) private String reason;
}
