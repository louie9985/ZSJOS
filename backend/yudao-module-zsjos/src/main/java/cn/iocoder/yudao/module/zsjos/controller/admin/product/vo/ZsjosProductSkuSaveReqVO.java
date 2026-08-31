package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ZsjosProductSkuSaveReqVO {
    private Long id;
    @NotNull private Long spuId;
    @NotBlank @Size(max = 200) private String skuName;
    @NotNull private Map<String, String> attrValues = new LinkedHashMap<>();
    @NotNull @DecimalMin("0") private BigDecimal price;
    /** Deprecated compatibility field. Status is controlled by the dedicated status endpoint. */
    private Integer status;
    @NotNull private Integer sort;
    @Size(max = 1000) private String remark;
}
