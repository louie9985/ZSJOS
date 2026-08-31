package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EamStockMinimumReqVO {
    @NotNull(message = "库存品项不能为空")
    private Long id;
    @NotNull(message = "最低库存不能为空")
    @Min(value = 0, message = "最低库存不能小于零")
    private Integer minimumQuantity;
}
