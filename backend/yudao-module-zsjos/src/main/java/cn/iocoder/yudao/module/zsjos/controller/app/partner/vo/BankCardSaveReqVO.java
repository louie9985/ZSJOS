package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BankCardSaveReqVO {
    @NotBlank @Size(max = 100) private String accountName;
    @NotBlank @Size(max = 40) private String cardNumber;
    @NotBlank @Size(max = 100) private String bankName;
    @Size(max = 100) private String branchName;
}
