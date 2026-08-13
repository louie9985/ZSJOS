package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Data
public class ZsjosProductCategorySaveReqVO {
    private Long id;
    private Long parentId;
    @NotBlank(message = "分类名称不能为空") @Size(max = 100)
    private String name;
    @DecimalMin(value = "0.00") private BigDecimal defaultValidCashbackAmount;
    @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000") private BigDecimal defaultDealCashbackRate;
    @NotNull(message = "状态不能为空") private Integer status;
    @NotNull(message = "排序不能为空") private Integer sort;
    @Size(max = 1000) private String remark;
}
