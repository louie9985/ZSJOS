package cn.iocoder.yudao.module.zsjos.controller.admin.production.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductionTicketSaveReqVO {
    @NotNull private Long accountId;
    private Long assigneeUserId;
    @NotBlank @Size(max = 64) private String idempotencyKey;
}
