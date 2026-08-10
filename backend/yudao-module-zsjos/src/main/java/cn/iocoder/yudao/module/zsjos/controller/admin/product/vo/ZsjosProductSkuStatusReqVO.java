package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ZsjosProductSkuStatusReqVO {
    @NotNull private Long id;
    @NotNull private Integer status;
}
