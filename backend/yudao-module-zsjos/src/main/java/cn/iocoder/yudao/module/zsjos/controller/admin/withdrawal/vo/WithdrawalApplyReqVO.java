package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class WithdrawalApplyReqVO {
    @NotEmpty private List<Long> cashbackIds;
    private Long bankCardId;
    @Size(max = 100) private String accountName;
    @Size(max = 40) private String cardNumber;
    @Size(max = 100) private String bankName;
    @Size(max = 100) private String branchName;
    private Boolean saveCard;
}
