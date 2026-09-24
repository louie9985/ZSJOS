package cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Only authorized source projections are populated; missing and denied never expose source identifiers. */
@Data
public class FinanceSourceRespVO {
    private String leadAccess;
    private Long leadId;
    private String leadNo;
    private String customerName;
    private String orderAccess;
    private Long orderId;
    private String orderNo;
    private String studentName;
    private String salesName;
    private String orderStatus;
    private String orderType;
    private String orderStatusLabel;
    private String orderTypeLabel;
    private BigDecimal orderTotalAmount;
    private BigDecimal orderPayableAmount;
    private LocalDateTime customerPaidAt;
    private String productName;
    private String skuName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal itemPayableAmount;
}
