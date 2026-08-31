package cn.iocoder.yudao.module.eam.controller.pub.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EamPublicAssetClearUsageReqVO {

    @NotNull(message = "版本不能为空")
    private Integer version;

}
