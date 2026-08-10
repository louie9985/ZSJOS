package cn.iocoder.yudao.module.system.controller.admin.ip.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AreaUpdateStatusReqVO {

    @NotNull
    private Integer id;

    @NotNull
    @InEnum(CommonStatusEnum.class)
    private Integer status;

}
