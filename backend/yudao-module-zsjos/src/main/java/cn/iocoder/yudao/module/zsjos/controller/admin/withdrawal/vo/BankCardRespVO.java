package cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo;

import lombok.Data;

@Data
public class BankCardRespVO {
    private Long id;
    private String accountName;
    private String maskedCardNumber;
    private String bankName;
    private String branchName;
    private Boolean defaultCard;
}
