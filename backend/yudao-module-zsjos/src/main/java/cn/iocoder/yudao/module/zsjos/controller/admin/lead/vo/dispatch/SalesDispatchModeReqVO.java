package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SalesDispatchModeReqVO {
    @NotNull
    private Boolean accepting;
}
