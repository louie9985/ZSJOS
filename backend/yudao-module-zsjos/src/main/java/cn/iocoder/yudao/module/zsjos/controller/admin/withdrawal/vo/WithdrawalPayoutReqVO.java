package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WithdrawalPayoutReqVO {
    private LocalDateTime paidAt;
    @Size(max = 500) private String remark;
}
