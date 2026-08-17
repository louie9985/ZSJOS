package cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CashbackRespVO {
    private Long id;
    private String cashbackNo;
    private String type;
    private String status;
    private Long beneficiaryUserId;
    private Long leadId;
    private String leadNo;
    private Long orderId;
    private Long orderItemId;
    private String productRefSnapshot;
    private String productNameSnapshot;
    private BigDecimal baseAmount;
    private BigDecimal rateSnapshot;
    private BigDecimal amount;
    private Integer observationDaysSnapshot;
    private LocalDateTime generatedAt;
    private LocalDateTime availableAt;
    private LocalDateTime settledAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
}
