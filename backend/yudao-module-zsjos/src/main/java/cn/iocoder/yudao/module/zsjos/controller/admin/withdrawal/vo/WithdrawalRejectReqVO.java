package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WithdrawalRejectReqVO {
    @NotBlank @Size(max = 500) private String reason;
}
