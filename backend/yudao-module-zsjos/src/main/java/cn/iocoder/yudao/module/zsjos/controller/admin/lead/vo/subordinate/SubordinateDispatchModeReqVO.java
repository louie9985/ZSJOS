package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubordinateDispatchModeReqVO extends SubordinateReasonReqVO {
    @NotNull(message = "接单状态不能为空")
    private Boolean accepting;
}
