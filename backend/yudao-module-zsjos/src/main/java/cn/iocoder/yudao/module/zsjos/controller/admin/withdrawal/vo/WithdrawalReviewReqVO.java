package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WithdrawalReviewReqVO {
    @NotNull @Min(0) private Integer version;
    @Size(max = 500) private String reason;
}
