package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class WithdrawalApplyReqVO {
    @NotEmpty private List<Long> cashbackIds;
    @NotBlank @Size(max = 100) private String accountName;
    @NotBlank @Size(max = 40) private String cardNumber;
    @NotBlank @Size(max = 100) private String bankName;
    @Size(max = 100) private String branchName;
    private Boolean saveCard;
}
