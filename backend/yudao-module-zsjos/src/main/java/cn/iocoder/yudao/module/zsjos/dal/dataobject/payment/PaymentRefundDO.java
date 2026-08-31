package cn.iocoder.yudao.module.zsjos.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("zsjos_payment_refund")
@KeySequence("zsjos_payment_refund_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class PaymentRefundDO extends TenantBaseDO {
    @TableId private Long id;
    private String refundNo;
    private Long purchaseIntentId;
    private Long paymentOrderId;
    private Long paymentTransactionId;
    private Long orderId;
    private BigDecimal refundAmount;
    private String currency;
    private String reason;
    private Long requesterUserId;
    private Long executorUserId;
    private String approvalMode;
    private String processInstanceId;
    private String status;
    private String provider;
    private String refundReqsn;
    private String originalReqsn;
    private String originalTrxId;
    private LocalDateTime acceptedAt;
    private LocalDateTime refundedAt;
    private LocalDateTime failedAt;
    private LocalDateTime lastQueriedAt;
    private LocalDateTime nextReconcileAt;
    private Integer retryCount;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String idempotencyKey;
    private Integer version;
}
