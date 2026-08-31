package cn.iocoder.yudao.module.zsjos.dal.mysql.cashback;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashbackStatusSummaryRow {
    private String status;
    private BigDecimal amount;
    private Long count;
}
