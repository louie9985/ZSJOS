package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class EamReceiptItemReqVO {
    @NotNull(message = "采购明细不能为空")
    private Long purchaseItemId;
    private Long stockBalanceId;
    @NotNull(message = "实际数量不能为空")
    @Min(value = 1, message = "实际数量必须大于零")
    private Integer quantity;
    private BigDecimal unitPrice;
    private List<String> serialNumbers;
    private Map<String, Object> actualExtFields;
}
