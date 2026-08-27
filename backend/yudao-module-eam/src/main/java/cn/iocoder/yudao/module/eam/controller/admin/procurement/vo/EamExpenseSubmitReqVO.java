package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EamExpenseSubmitReqVO {
    @NotNull(message = "实际金额不能为空")
    @DecimalMin(value = "0.00", message = "实际金额不能小于零")
    private BigDecimal actualAmount;
    private List<String> fileUrls;
}
