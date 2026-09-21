package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WithdrawalBatchPayoutReqVO extends WithdrawalPayoutReqVO {
    @NotEmpty @Size(max = 100)
    private List<@NotNull @Positive Long> ids;
}
