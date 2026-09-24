package cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CashbackWithdrawalRespVO {
    private Long id;
    private String withdrawalNo;
    private BigDecimal amount;
    private Boolean active;
    private String status;
    private LocalDateTime submittedAt;
}
