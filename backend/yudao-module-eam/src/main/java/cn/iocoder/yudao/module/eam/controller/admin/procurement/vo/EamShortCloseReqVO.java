package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EamShortCloseReqVO {
    @NotNull(message = "采购明细不能为空")
    private Long purchaseItemId;
    @NotNull(message = "少到关闭数量不能为空")
    @Min(value = 1, message = "少到关闭数量必须大于零")
    private Integer quantity;
    private String reason;
}
