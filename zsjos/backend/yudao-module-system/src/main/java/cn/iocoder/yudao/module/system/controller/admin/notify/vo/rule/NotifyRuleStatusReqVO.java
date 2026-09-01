package cn.iocoder.yudao.module.system.controller.admin.notify.vo.rule;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotifyRuleStatusReqVO {

    @NotNull(message = "规则编号不能为空")
    private Long id;

    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "状态必须是 {value}")
    private Integer status;
}
