package cn.iocoder.yudao.module.eam.controller.admin.procurement.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class EamDemandItemReqVO {
    @NotBlank(message = "需求物品名称不能为空")
    private String name;
    @NotNull(message = "资产分类不能为空")
    private Long categoryId;
    @NotNull(message = "需求数量不能为空")
    @Min(value = 1, message = "需求数量必须大于零")
    private Integer quantity;
    private String unit;
    private Map<String, Object> extFields;
    private Map<String, String> extFieldLabels;
}
