package cn.iocoder.yudao.module.zsjos.controller.admin.product.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ZsjosProductValidateReqVO {
    @NotEmpty
    private List<String> productRefs;
}
