package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EamStockAllocateReqVO {
    @NotNull(message = "库存预留不能为空")
    private Long reservationId;
}
