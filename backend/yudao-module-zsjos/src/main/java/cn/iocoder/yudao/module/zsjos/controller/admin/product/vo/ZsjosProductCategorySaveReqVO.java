package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ZsjosProductCategorySaveReqVO {
    private Long id;
    private Long parentId;
    @NotBlank(message = "分类名称不能为空") @Size(max = 100)
    private String name;
    @NotNull(message = "状态不能为空") private Integer status;
    @NotNull(message = "排序不能为空") private Integer sort;
    @Size(max = 1000) private String remark;
}
