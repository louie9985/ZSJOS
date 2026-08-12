package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubordinateAccountStatusReqVO extends SubordinateReasonReqVO {
    @NotNull(message = "账号状态不能为空")
    private Integer status;
}
