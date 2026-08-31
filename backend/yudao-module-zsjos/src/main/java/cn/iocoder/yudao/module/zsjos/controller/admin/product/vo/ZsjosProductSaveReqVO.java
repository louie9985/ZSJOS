package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Data
public class ZsjosProductSaveReqVO {
    private Long id;
    @NotNull(message = "叶子分类不能为空")
    private Long categoryId;
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 200, message = "产品名称不能超过 200 个字符")
    private String name;
    @Size(max = 200) private String subtitle;
    private String description;
    @Size(max = 500) private String targetAudience;
    @Size(max = 100) private String studyDuration;
    @Size(max = 100) private String studyMode;
    @Size(max = 1024) private String coverImage;
    @DecimalMin(value = "0.00") private BigDecimal validCashbackAmount;
    @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000") private BigDecimal dealCashbackRate;
    @NotNull(message = "状态不能为空")
    private Integer status;
    @NotNull(message = "排序不能为空")
    private Integer sort;
    @Size(max = 1000, message = "备注不能超过 1000 个字符")
    private String remark;
}
