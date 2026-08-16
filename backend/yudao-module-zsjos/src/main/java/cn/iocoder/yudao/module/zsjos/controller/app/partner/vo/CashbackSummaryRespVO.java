package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CashbackSummaryRespVO {
    private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);
    private BigDecimal pendingAmount = BigDecimal.ZERO.setScale(2);
    private BigDecimal availableAmount = BigDecimal.ZERO.setScale(2);
    private BigDecimal withdrawingAmount = BigDecimal.ZERO.setScale(2);
    private BigDecimal withdrawnAmount = BigDecimal.ZERO.setScale(2);
    private Map<String, Long> counts = Map.of();
}
