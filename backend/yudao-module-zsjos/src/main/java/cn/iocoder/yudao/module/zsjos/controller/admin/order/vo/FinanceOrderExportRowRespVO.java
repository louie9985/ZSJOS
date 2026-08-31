package cn.iocoder.yudao.module.zsjos.controller.admin.order.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceOrderExportRowRespVO {
    private String orderNo;
    private String orderType;
    private String status;
    private String buyerName;
    private String studentName;
    private String studentMobile;
    private String studentWechatId;
    private String region;
    private String courseSummary;
    private BigDecimal totalAmount;
    private LocalDateTime customerPaidAt;
    private String paymentMethod;
    private String formalSalesName;
    private String submitterName;
    private LocalDateTime submittedAt;
    private LocalDateTime effectiveAt;
    private Integer approvalRoundNo;
    private String registrationStatus;
    private String registrationReviewer;
    private LocalDateTime registrationReviewedAt;
    private String financeStatus;
    private String financeReviewer;
    private LocalDateTime financeReviewedAt;
    private String finalReason;
}
