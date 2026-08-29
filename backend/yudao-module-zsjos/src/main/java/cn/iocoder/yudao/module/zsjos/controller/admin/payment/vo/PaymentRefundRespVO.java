package cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRefundRespVO {
    private Long id; private String refundNo; private Long paymentTransactionId; private Long orderId;
    private BigDecimal refundAmount; private String currency; private String reason; private String approvalMode;
    private String status; private String refundReqsn; private String originalReqsn; private String originalTrxId;
    private LocalDateTime acceptedAt; private LocalDateTime refundedAt; private LocalDateTime lastQueriedAt;
    private String lastErrorMessage;
}
