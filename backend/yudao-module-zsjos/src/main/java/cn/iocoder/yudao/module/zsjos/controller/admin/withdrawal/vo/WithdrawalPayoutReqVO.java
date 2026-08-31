package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WithdrawalPayoutReqVO {
    @NotBlank @Size(max = 100) private String bankTransactionNo;
    @NotNull private Long proofFileId;
    @Size(max = 500) private String remark;
}
