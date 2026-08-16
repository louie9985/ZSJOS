package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawalSummaryRespVO {
    private BigDecimal availableAmount = BigDecimal.ZERO.setScale(2);
    private BigDecimal minimumAmount = BigDecimal.ZERO.setScale(2);
    private Long selectableCount;
    private Boolean canApply;
}
