package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EamStockReserveReqVO {
    @NotNull(message = "需求明细不能为空")
    private Long demandItemId;
    private Long assetId;
    private Long stockBalanceId;
    @Min(value = 1, message = "预留数量必须大于零")
    private Integer quantity = 1;
}
